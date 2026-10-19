/* =========================
   Platform Shutdown Control UI
   ========================= */

(function() {
    'use strict';

    // Configuration
    const CONFIG = {
        shutdownEndpoint: '/actuator/shutdown',
        statusEndpoint: '/actuator/shutdown-status',
        pollInterval: 2000,
        drainTimeout: 20000, // 20 seconds default, will be updated from server
        confirmTokenHeader: 'X-Shutdown-Token'
    };

    // State
    let state = {
        isDraining: false,
        drainStartTime: null,
        pollTimer: null,
        modalOpen: false
    };

    // DOM Elements
    let elements = {};

    function cacheElements() {
        elements = {
            // Banner elements
            banner: document.getElementById('shutdown-banner'),
            countdown: document.getElementById('shutdown-countdown'),
            progressBar: document.getElementById('shutdown-progress-bar'),
            statusText: document.getElementById('shutdown-status-text'),

            // Modal elements
            triggerBtn: document.getElementById('shutdown-trigger-btn'),
            modal: document.getElementById('shutdown-confirm-modal'),
            modalClose: document.getElementById('modal-close-btn'),
            modalCancel: document.getElementById('modal-cancel-btn'),
            modalConfirm: document.getElementById('modal-confirm-btn'),
            confirmUnderstand: document.getElementById('confirm-understand'),
            confirmAuthorized: document.getElementById('confirm-authorized'),
            drainTimeoutEl: document.getElementById('drain-timeout'),

            // Status panel elements
            statusPanel: document.getElementById('shutdown-status-panel'),
            statusPhase: document.getElementById('status-phase'),
            statusInflight: document.getElementById('status-inflight'),
            statusProgress: document.getElementById('status-progress'),
            statusElapsed: document.getElementById('status-elapsed'),
            statusProgressBar: document.getElementById('status-progress-bar'),
            statusLog: document.getElementById('status-log')
        };
    }

    function init() {
        cacheElements();
        bindEvents();
        checkShutdownStatus(); // Check if shutdown already in progress on page load
    }

    function bindEvents() {
        // Trigger button
        if (elements.triggerBtn) {
            elements.triggerBtn.addEventListener('click', openModal);
        }

        // Modal close buttons
        if (elements.modalClose) {
            elements.modalClose.addEventListener('click', closeModal);
        }
        if (elements.modalCancel) {
            elements.modalCancel.addEventListener('click', closeModal);
        }

        // Modal backdrop click to close
        if (elements.modal) {
            elements.modal.querySelector('.modal-backdrop')?.addEventListener('click', closeModal);
        }

        // Checkbox validation
        [elements.confirmUnderstand, elements.confirmAuthorized].forEach(cb => {
            if (cb) {
                cb.addEventListener('change', updateConfirmButton);
            }
        });

        // Confirm button
        if (elements.modalConfirm) {
            elements.modalConfirm.addEventListener('click', initiateShutdown);
        }

        // Keyboard support
        document.addEventListener('keydown', handleKeydown);

        // Prevent form submissions in admin section from interfering
        const adminSection = document.getElementById('shutdownControl');
        if (adminSection) {
            adminSection.addEventListener('submit', e => e.preventDefault());
        }
    }

    function handleKeydown(e) {
        if (!state.modalOpen) return;

        if (e.key === 'Escape') {
            closeModal();
        } else if (e.key === 'Tab') {
            trapFocus(e);
        }
    }

    function trapFocus(e) {
        const focusableElements = elements.modal.querySelectorAll(
            'button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])'
        );
        const firstElement = focusableElements[0];
        const lastElement = focusableElements[focusableElements.length - 1];

        if (e.shiftKey && document.activeElement === firstElement) {
            e.preventDefault();
            lastElement.focus();
        } else if (!e.shiftKey && document.activeElement === lastElement) {
            e.preventDefault();
            firstElement.focus();
        }
    }

    function openModal() {
        if (!elements.modal) return;

        state.modalOpen = true;
        elements.modal.showModal();
        elements.confirmUnderstand?.focus();
        updateConfirmButton();
        document.body.style.overflow = 'hidden';

        // Announce to screen readers
        announceToScreenReader('Platform shutdown confirmation dialog opened');
    }

    function closeModal() {
        if (!elements.modal) return;

        state.modalOpen = false;
        elements.modal.close();
        elements.confirmUnderstand.checked = false;
        elements.confirmAuthorized.checked = false;
        document.body.style.overflow = '';

        // Return focus to trigger button
        elements.triggerBtn?.focus();
    }

    function updateConfirmButton() {
        const bothChecked = elements.confirmUnderstand?.checked && elements.confirmAuthorized?.checked;
        if (elements.modalConfirm) {
            elements.modalConfirm.disabled = !bothChecked;
        }
    }

    async function initiateShutdown() {
        closeModal();

        // Show status panel
        showStatusPanel();

        try {
            // Call admin endpoint to trigger shutdown (server-side token handling)
            const response = await fetch('/admin/shutdown', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });

            if (!response.ok) {
                const error = await response.json().catch(() => ({ message: 'Shutdown request failed' }));
                throw new Error(error.message || `HTTP ${response.status}`);
            }

            const data = await response.json();
            logStatus('Shutdown initiated successfully', 'success');

            // Start polling for status
            startStatusPolling();

        } catch (error) {
            logStatus(`Failed to initiate shutdown: ${error.message}`, 'error');
            hideStatusPanel();
            showNotification('Shutdown failed: ' + error.message, 'error');
        }
    }

    function showStatusPanel() {
        if (elements.statusPanel) {
            elements.statusPanel.classList.remove('hidden');
            elements.statusPanel.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
        if (elements.triggerBtn) {
            elements.triggerBtn.disabled = true;
            elements.triggerBtn.textContent = 'Shutdown Initiated';
        }
    }

    function hideStatusPanel() {
        if (elements.statusPanel) {
            elements.statusPanel.classList.add('hidden');
        }
        if (elements.triggerBtn) {
            elements.triggerBtn.disabled = false;
            elements.triggerBtn.textContent = 'Initiate Platform Shutdown';
        }
    }

    function startStatusPolling() {
        state.isDraining = true;
        state.drainStartTime = Date.now();

        // Initial fetch
        pollStatus();

        // Set up interval
        state.pollTimer = setInterval(pollStatus, CONFIG.pollInterval);
    }

    function stopStatusPolling() {
        state.isDraining = false;
        if (state.pollTimer) {
            clearInterval(state.pollTimer);
            state.pollTimer = null;
        }
    }

    async function pollStatus() {
        try {
            const response = await fetch(CONFIG.statusEndpoint);
            if (!response.ok) {
                if (response.status === 404) {
                    // Status endpoint not available, might be fully shut down
                    handleShutdownComplete();
                    return;
                }
                throw new Error(`HTTP ${response.status}`);
            }

            const data = await response.json();
            updateStatusDisplay(data);

            // Check if drain is complete
            if (data.draining === false && data.complete === true) {
                handleShutdownComplete();
            }

        } catch (error) {
            logStatus(`Status poll error: ${error.message}`, 'error');
            // If we can't reach status endpoint, shutdown might be complete
            if (error.message.includes('Failed to fetch') || error.message.includes('NetworkError')) {
                handleShutdownComplete();
            }
        }
    }

    function updateStatusDisplay(data) {
        // Update drain timeout display
        if (data.drainTimeoutMs) {
            CONFIG.drainTimeout = data.drainTimeoutMs;
            if (elements.drainTimeoutEl) {
                elements.drainTimeoutEl.textContent = `${(data.drainTimeoutMs / 1000).toFixed(0)}s`;
            }
        }

        // Update status panel
        if (elements.statusPhase) {
            elements.statusPhase.textContent = getPhaseLabel(data);
        }
        if (elements.statusInflight) {
            elements.statusInflight.textContent = data.inFlightRequests ?? '—';
        }
        if (elements.statusProgress) {
            const progress = calculateProgress(data);
            elements.statusProgress.textContent = `${progress}%`;
        }
        if (elements.statusElapsed) {
            const elapsed = state.drainStartTime ? Math.round((Date.now() - state.drainStartTime) / 1000) : 0;
            elements.statusElapsed.textContent = `${elapsed}s`;
        }
        if (elements.statusProgressBar) {
            const progress = calculateProgress(data);
            elements.statusProgressBar.style.width = `${progress}%`;
        }

        // Update banner (visible to all users)
        updateBanner(data);
    }

    function getPhaseLabel(data) {
        if (!data.draining) return 'Not Started';
        if (data.readinessChanged) return 'Readiness: OUT_OF_SERVICE';
        if (data.deregistering) return 'Deregistering from Service Discovery';
        if (data.draining) return 'Draining In-Flight Requests';
        if (data.complete) return 'Shutdown Complete';
        return 'Shutting Down...';
    }

    function calculateProgress(data) {
        if (!data.draining) return 0;
        if (data.complete) return 100;

        // Estimate progress based on in-flight requests and time
        const elapsed = state.drainStartTime ? (Date.now() - state.drainStartTime) : 0;
        const timeProgress = Math.min((elapsed / CONFIG.drainTimeout) * 100, 90);

        // If we have in-flight count, factor it in
        if (typeof data.inFlightRequests === 'number') {
            if (data.inFlightRequests === 0) return 100;
            // Assume requests drain linearly
            return Math.min(timeProgress, 90);
        }

        return timeProgress;
    }

    function updateBanner(data) {
        if (!elements.banner) return;

        if (data.draining) {
            elements.banner.classList.remove('hidden');

            // Update countdown
            const elapsed = state.drainStartTime ? (Date.now() - state.drainStartTime) : 0;
            const remaining = Math.max(0, CONFIG.drainTimeout - elapsed);
            const minutes = Math.floor(remaining / 60000);
            const seconds = Math.floor((remaining % 60000) / 1000);

            if (elements.countdown) {
                elements.countdown.textContent = `Shutdown in ${minutes}:${seconds.toString().padStart(2, '0')}`;
            }

            // Update progress bar
            const progress = calculateProgress(data);
            if (elements.progressBar) {
                elements.progressBar.style.width = `${progress}%`;
            }

            // Update status text
            if (elements.statusText) {
                elements.statusText.textContent = getPhaseLabel(data);
            }

            // Disable transactional forms
            disableTransactionalForms();

        } else {
            elements.banner.classList.add('hidden');
            enableTransactionalForms();
        }
    }

    function disableTransactionalForms() {
        // Disable all forms and buttons that submit transactions
        const transactionalElements = document.querySelectorAll(
            'form:not(#shutdownControl form), button[type="submit"], input[type="submit"], a[href*="create"], a[href*="submit"], a[href*="execute"]'
        );

        transactionalElements.forEach(el => {
            if (!el.dataset.shutdownDisabled) {
                el.dataset.shutdownDisabled = 'true';
                el.disabled = true;
                el.style.opacity = '0.5';
                el.style.cursor = 'not-allowed';
                el.title = 'Disabled during platform shutdown';
            }
        });
    }

    function enableTransactionalForms() {
        const transactionalElements = document.querySelectorAll('[data-shutdown-disabled="true"]');
        transactionalElements.forEach(el => {
            el.disabled = false;
            el.style.opacity = '';
            el.style.cursor = '';
            el.title = '';
            delete el.dataset.shutdownDisabled;
        });
    }

    function handleShutdownComplete() {
        stopStatusPolling();
        logStatus('Platform shutdown complete. All services have terminated.', 'success');

        // Show completion in banner
        if (elements.statusText) {
            elements.statusText.textContent = 'Platform shutdown complete';
        }
        if (elements.countdown) {
            elements.countdown.textContent = 'Platform is offline';
        }
        if (elements.progressBar) {
            elements.progressBar.style.width = '100%';
        }
        if (elements.statusProgressBar) {
            elements.statusProgressBar.style.width = '100%';
        }
        if (elements.statusProgress) {
            elements.statusProgress.textContent = '100%';
        }
        if (elements.statusPhase) {
            elements.statusPhase.textContent = 'Shutdown Complete';
        }

        // Disable further actions
        if (elements.triggerBtn) {
            elements.triggerBtn.disabled = true;
            elements.triggerBtn.textContent = 'Platform Offline';
        }

        // Announce to screen readers
        announceToScreenReader('Platform shutdown complete. All services are offline.');
    }

    function checkShutdownStatus() {
        // Check if shutdown is already in progress on page load
        fetch(CONFIG.statusEndpoint)
            .then(response => {
                if (response.ok) return response.json();
                throw new Error('Status not available');
            })
            .then(data => {
                if (data.draining) {
                    state.isDraining = true;
                    state.drainStartTime = Date.now(); // Approximate
                    startStatusPolling();
                    showStatusPanel();
                }
            })
            .catch(() => {
                // No shutdown in progress, normal state
            });
    }

    function logStatus(message, type = 'info') {
        if (!elements.statusLog) return;

        const entry = document.createElement('div');
        entry.className = `status-log-entry status-log-${type}`;
        entry.innerHTML = `
            <span class="status-log-time">[${new Date().toLocaleTimeString()}]</span>
            <span class="status-log-message">${escapeHtml(message)}</span>
        `;

        elements.statusLog.appendChild(entry);
        elements.statusLog.scrollTop = elements.statusLog.scrollHeight;
    }

    function showNotification(message, type = 'info') {
        // Simple toast notification
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;
        toast.textContent = message;
        toast.setAttribute('role', 'alert');
        toast.setAttribute('aria-live', 'polite');

        document.body.appendChild(toast);

        // Animate in
        requestAnimationFrame(() => {
            toast.classList.add('toast-show');
        });

        // Remove after delay
        setTimeout(() => {
            toast.classList.remove('toast-show');
            setTimeout(() => toast.remove(), 300);
        }, 5000);
    }

    function announceToScreenReader(message) {
        const announcer = document.getElementById('sr-announcer') || createAnnouncer();
        announcer.textContent = message;
    }

    function createAnnouncer() {
        const announcer = document.createElement('div');
        announcer.id = 'sr-announcer';
        announcer.className = 'sr-only';
        announcer.setAttribute('aria-live', 'polite');
        announcer.setAttribute('aria-atomic', 'true');
        document.body.appendChild(announcer);
        return announcer;
    }

    function escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }

    // Initialize when DOM is ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
})();