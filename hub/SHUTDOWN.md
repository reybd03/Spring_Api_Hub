# Graceful Shutdown and Traffic Draining

How this service stops taking work before it stops existing, and how to ask it to shut down.

## What happens on shutdown

Spring publishes `ContextClosedEvent` before it stops any lifecycle bean or destroys anything, so
the datasource, repositories and HTTP server are all still usable when the drain starts. That
event is the hook `GracefulShutdownCoordinator` uses, which means one code path covers every
trigger: `POST /actuator/shutdown`, `SIGTERM` from an orchestrator, `docker stop`, and Ctrl+C.

The drain runs in a deliberate order:

1. **Readiness flips to `REFUSING_TRAFFIC`.** This drives `/actuator/health/readiness` to
   `OUT_OF_SERVICE`, which is the signal gateways and orchestrators actually watch. It goes first
   because it is the only signal every caller in the platform honours, including ones that never
   see this JVM's sockets.
2. **New business requests are refused** with `503` and `Retry-After: 5`. Local refusal does not
   wait on the upstream noticing.
3. **Deregistration runs.** With no discovery client configured this only logs; a discovery
   client needs no special wiring, just another `ServiceDeregistration` bean.
4. **Streaming feeds and message buffers are completed.** See below, this is the step that
   matters most here.
5. **In-flight requests are awaited**, up to `platform.shutdown.drain-timeout`.

Then Spring's own `server.shutdown=graceful` stops the listener and waits for whatever
connections remain, and only afterwards are beans destroyed and the connection pool closed.

### Readiness fails while liveness stays up

This is intentional. A draining instance must fail readiness so it leaves the load balancer, but
must stay live so an orchestrator does not conclude it is wedged and kill it outright, cutting the
drain short.

### Why the streaming feeds are terminated

Both Jenkins SSE feeds poll every second and are designed never to end. Without an explicit
signal, an already-open feed stays counted as in-flight work, so every shutdown would sit out the
full drain timeout waiting for a stream that was never going to finish. They now compose with
`takeUntilOther(drainSignal.drain())`, which ends them cleanly. A browser `EventSource`
reconnects on its own and is then refused by the drain gate, rather than being handed a stream
that closes immediately and spinning it in a reconnect loop.

`AutomationService.hotSink` is registered with `MessageBufferRegistry` so it is completed on
drain rather than discarded with subscribers still attached.

## Enabling the shutdown endpoint

The endpoint ships **disabled**. Spring Boot leaves
`management.endpoint.shutdown.access` at `none`, and the platform configuration sets it to
`unrestricted` so the endpoint is exposed and then guarded, because an unauthenticated kill
switch on a service that holds a database connection and drives host processes is not an
acceptable default.

`ShutdownTokenWebFilter` fails closed:

| `platform.shutdown.shutdown-token` | Request | Response |
| --- | --- | --- |
| blank (the default) | anything, including a correct guess | `404` |
| set, token wrong or missing | — | `401` |
| set, token correct | — | `200`, instance drains and closes |

With no secret provisioned the endpoint answers `404` rather than `401` on purpose: it should be
indistinguishable from one that was never exposed, so it cannot be probed for. Comparison is
constant-time.

To enable it, provide the secret from the environment:

```bash
export HUB_SHUTDOWN_TOKEN="$(openssl rand -hex 32)"
```

Never commit it. `application.properties` reads it from `HUB_SHUTDOWN_TOKEN` and defaults to
blank, which is the safe posture.

## Configuration

```properties
server.shutdown=graceful
spring.lifecycle.timeout-per-shutdown-phase=30s

management.endpoints.web.exposure.include=health,info,shutdown
management.endpoint.shutdown.access=unrestricted
management.endpoint.health.probes.enabled=true
management.endpoint.health.show-details=never

platform.shutdown.drain-timeout=20s
platform.shutdown.shutdown-token=${HUB_SHUTDOWN_TOKEN:}
```

Keep `drain-timeout` below `timeout-per-shutdown-phase`. The drain has to win that race, or
Spring cuts the process off mid-drain and leaves in-flight requests truncated.

`management.endpoints.web.exposure.include` is an allowlist, not a deny list. Anything not named
is not served, so adding a name such as `metrics` widens the surface and should be deliberate.

## Operational notes

- **Probes.** `health/liveness` and `health/readiness` are separate. Point the load balancer at
  readiness and the orchestrator at liveness.
- **Health details.** `show-details=never` keeps component detail off non-loopback callers.
- **Management traffic is never counted** as in-flight work, or the shutdown request would wait
  on itself. Health endpoints stay reachable throughout, so a gateway can observe the instance
  leaving rotation.
- **Draining is per-instance and one-shot.** Once drained, the instance refuses traffic until it
  is restarted. The drain guard is an `AtomicBoolean`, so a retried shutdown cannot re-signal
  every sink.
- **Drain timeouts are logged as a warning** naming the number of requests still running, so a
  recurring timeout is a signal that a request path is too slow rather than just noise.

## Tests

| Test | Covers |
| --- | --- |
| `GracefulShutdownCoordinatorTest` | Step ordering, drain-once guard, deregistration failures |
| `ShutdownTokenWebFilterTest` | Fail-closed, wrong/missing token, non-shutdown paths |
| `DrainGateWebFilterTest` | Refusal during drain, management exemption, counter release |
| `InFlightRequestTrackerTest` | Counting, quiescence, timeout |
| `DrainSignalTest`, `MessageBufferRegistryTest` | Signal completion, buffer termination |
| `ActuatorPathsTest` | Base path normalisation |
| `GracefulShutdownHttpTests` | Real HTTP: exposure, probes, 503 on drain |
| `DefaultDenyShutdownTests` | Shipped default denies shutdown over HTTP |
| `ConfiguredShutdownSecretTests` | Authorised shutdown closes the instance |
| `SseDrainTests` | Live SSE feed ends on drain |

`GracefulShutdownCoordinatorTest` uses `AnnotationConfigApplicationContext` rather than
`GenericApplicationContext`, because only the former processes `@EventListener` and the
`ContextClosedEvent` wiring is precisely what needs testing.