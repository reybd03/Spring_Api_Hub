// 2. Dispatch operations without blocking or reloading UI
function sendControl(target, action) {
    const logger = document.getElementById('log-output');
    const controls = document.getElementById('performance-stats');
    
    logger.innerText = `Dispatching command: Requesting ${action} on ${target}...`;

    fetch(`/products/automation/jenkins/actions?target=${target}&action=${action}`, { method: 'POST' })
        .then(res => res.json())
        .then(data => {
            logger.innerText = `System Response: [${data.status}] — ${data.message}`;
        })
        .catch(err => {
            logger.innerText = `Network routing failure: ${err}`;
        });
}