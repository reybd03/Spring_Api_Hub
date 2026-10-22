# Thymeleaf Fragments

Share layout via `th:fragment` in `fragments/layout.html`:

```html
<head th:fragment="head(pageTitle)">...</head>
<div id="header" th:fragment="header(pageTitle)">...</div>
<div th:fragment="scripts">...</div>
```

- Include with `th:replace`/`th:insert` and pass `pageTitle`.
- Resource URLs use `@{/css/main.css}`, `@{/js/main.js}` (context-relative).
- Nav items come from model `navItems` (path/title).
- Global `scripts` fragment loads `main.js` only.

Exceptions: standalone pages that must not load global scripts (e.g., minimal error pages).
