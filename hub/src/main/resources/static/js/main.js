const parentList = document.getElementById('nav-list');
const listItems = parentList.querySelectorAll('li');

let target = "Home"

listItems.forEach(item => {
    if (item.innerText.trim() === target) {
        parentList.prepend(item);
    }
})

/* =========================
   Dark/Light Theme Toggle Logic
   ========================= */

// Target the toggle button element
const themeToggleBtn = document.getElementById('theme-toggle');

// Check localStorage for a saved theme preference, default to light
const currentTheme = localStorage.getItem('theme') || 'light';

// Apply the initial saved theme to the document root element
document.documentElement.setAttribute('data-theme', currentTheme);

// Handle the button click event
themeToggleBtn.addEventListener('click', () => {
  // Get the active theme attribute value
  const activeTheme = document.documentElement.getAttribute('data-theme');
  
  // Determine the new theme value
  let newTheme = 'light';
  if (activeTheme === 'light') {
    newTheme = 'dark';
  }
  
  // Update the HTML data-theme attribute
  document.documentElement.setAttribute('data-theme', newTheme);
  
  // Persist the user selection to localStorage
  localStorage.setItem('theme', newTheme);
});