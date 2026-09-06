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
    // console.log(metrics.uptime);
    document.getElementById('systemCpuLoad-display').innerText = metrics.systemCpuLoad;
    document.getElementById('processCpuLoad-display').innerText = metrics.processCpuLoad;
    document.getElementById('totalMemory-display').innerText = metrics.totalMemory;
    document.getElementById('freeMemory-display').innerText = metrics.freeMemory;
    document.getElementById('usedMemory-display').innerText = metrics.usedMemory;
    document.getElementById('uptime-display').innerText = metrics.uptime;
});

configSource.addEventListener('config', function (event) {
    const config = JSON.parse(event.data);
    // console.log(config.productDiscoveryStatus);
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

