# CSS Theming

Theme via CSS variables on `:root` and `[data-theme="dark"]`:

```css
:root { --bg-color: #ffffff; --text-color: #1a1a1a; }
[data-theme="dark"] { --bg-color: #121212; --text-color: #f5f5f5; }
```

- Apply with `document.documentElement.setAttribute('data-theme', theme)`.
- Persist choice in `localStorage` (`theme` key), default `light`.
- Consume variables everywhere: `background-color: var(--bg-color);`.
- Keep color pairs (bg/text) in both themes.
- No external theming framework; tokens are the source of truth.

Exceptions: third-party widgets that don't read CSS vars.
