# Vanilla JS IIFE Module

Wrap feature scripts in an IIFE:

```js
(function() {
    'use strict';

    const CONFIG = { /* endpoints, timing */ };
    const state = { /* mutable UI state */ };
    let elements = {}; // cached DOM refs

    function cacheElements() { ... }
    function bindEvents() { ... }
    function init() { cacheElements(); bindEvents(); }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();
```

- Cache DOM lookups in `elements` after init.
- Keep config/state separate from DOM.
- Guard optional elements with `if (el)`.
- No bundler/build step required; scripts are plain `<script>` tags.

Exceptions: code that must be imported by other scripts (use ES modules then).
