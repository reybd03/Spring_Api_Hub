// Get the complete URL
const currentUrl = window.location.href;

const apiTarget = currentUrl.indexOf("/products");
const apiEndpoint = currentUrl !== -1 ? currentUrl.substring(apiTarget) : currentUrl;
console.log(apiEndpoint);

// 1. Establish SSE pipeline hook directly back to our continuous flux stream
const eventSource = new EventSource(apiEndpoint + '/streamSysStats');
const configSource = new EventSource(apiEndpoint + '/streamConfig');

eventSource.addEventListener('sys-stats', function (event) {
    const metrics = JSON.parse(event.data);
    document.getElementById('systemCpuLoad-display').innerText = metrics.systemCpuLoad;
    document.getElementById('processCpuLoad-display').innerText = metrics.processCpuLoad;
    document.getElementById('totalMemory-display').innerText = metrics.totalMemory;
    document.getElementById('freeMemory-display').innerText = metrics.freeMemory;
    document.getElementById('usedMemory-display').innerText = metrics.usedMemory;
});

configSource.addEventListener('config', function (event) {
    const config = JSON.parse(event.data);
    document.getElementById('productName').innerText = config.productName;
    document.getElementById('productDiscovered').innerText = config.productDiscovered;
    document.getElementById('productDiscoveryStatus').innerText = config.productDiscoveryStatus;
    document.getElementById('productBasePath').innerText = config.productBasePath;
    document.getElementById('productUserName').innerText = config.productUserName;
    document.getElementById('productPassword').innerText = config.productPassword;
    document.getElementById('productAPIKey').innerText = config.productAPIKey;
    document.getElementById('productURL').innerText = config.productURL;
    document.getElementById('productPort').innerText = config.productPort;
});

eventSource.onerror = function () {
    console.error("SSE Streaming connection dropped temporarily.");
};

// 2. Dispatch operations without blocking or reloading UI
function sendControl(target, action) {
    const logger = document.getElementById('log-output');
    logger.innerText = `Dispatching command: Requesting ${action} on ${target}...`;

    fetch(`/products/automation/jenkins/action?target=${target}&action=${action}`, { method: 'POST' })
        .then(res => res.json())
        .then(data => {
            logger.innerText = `System Response: [${data.status}] — ${data.message}`;
        })
        .catch(err => {
            logger.innerText = `Network routing failure: ${err}`;
        });
}