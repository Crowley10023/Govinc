/**
 * Global Authorization and Error Handler
 * 
 * Handles 403 Forbidden responses with user-friendly messages.
 * Intercepts fetch/AJAX calls and displays appropriate error messages.
 */

(function() {
    'use strict';

    /**
     * Display a user-friendly error message for authorization failures.
     * Uses a modal or toast notification depending on availability.
     */
    function showForbiddenError(message) {
        message = message || "You do not have permission to perform this action.";
        
        // Try to show Bootstrap modal if available
        if (typeof bootstrap !== 'undefined' && typeof bootstrap.Modal !== 'undefined') {
            showErrorModal(message);
        } else if (typeof $ !== 'undefined') {
            // Fallback to Bootstrap 4 modal if jQuery available
            showErrorModalBootstrap4(message);
        } else {
            // Fallback to alert
            alert("Access Denied:\n\n" + message);
        }
    }

    /**
     * Show error using Bootstrap 5 modal
     */
    function showErrorModal(message) {
        // Create modal HTML if not exists
        let modal = document.getElementById('authErrorModal');
        if (!modal) {
            const modalHtml = `
                <div class="modal fade" id="authErrorModal" tabindex="-1" aria-labelledby="authErrorModalLabel" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered">
                        <div class="modal-content">
                            <div class="modal-header bg-danger text-white">
                                <h5 class="modal-title" id="authErrorModalLabel">Access Denied</h5>
                                <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close"></button>
                            </div>
                            <div class="modal-body">
                                <p id="authErrorMessage"></p>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-bs-dismiss="modal">Close</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            document.body.insertAdjacentHTML('beforeend', modalHtml);
            modal = document.getElementById('authErrorModal');
        }
        
        // Update message
        document.getElementById('authErrorMessage').textContent = message;
        
        // Show modal
        const bsModal = new bootstrap.Modal(modal);
        bsModal.show();
    }

    /**
     * Show error using Bootstrap 4 modal (jQuery)
     */
    function showErrorModalBootstrap4(message) {
        // Create modal HTML if not exists
        if ($('#authErrorModal').length === 0) {
            const modalHtml = `
                <div class="modal fade" id="authErrorModal" tabindex="-1" role="dialog" aria-labelledby="authErrorModalLabel" aria-hidden="true">
                    <div class="modal-dialog modal-dialog-centered" role="document">
                        <div class="modal-content">
                            <div class="modal-header bg-danger text-white">
                                <h5 class="modal-title" id="authErrorModalLabel">Access Denied</h5>
                                <button type="button" class="close text-white" data-dismiss="modal" aria-label="Close">
                                    <span aria-hidden="true">&times;</span>
                                </button>
                            </div>
                            <div class="modal-body">
                                <p id="authErrorMessage"></p>
                            </div>
                            <div class="modal-footer">
                                <button type="button" class="btn btn-secondary" data-dismiss="modal">Close</button>
                            </div>
                        </div>
                    </div>
                </div>
            `;
            $(document.body).append(modalHtml);
        }
        
        // Update message and show
        $('#authErrorMessage').text(message);
        $('#authErrorModal').modal('show');
    }

    /**
     * Intercept native fetch calls
     */
    const originalFetch = window.fetch;
    window.fetch = function(...args) {
        return originalFetch.apply(this, args).then(response => {
            if (response.status === 403) {
                // Handle 403 Forbidden
                response.clone().json().then(data => {
                    const message = data.message || "You do not have permission to perform this action.";
                    showForbiddenError(message);
                }).catch(() => {
                    showForbiddenError("You do not have permission to perform this action.");
                });
            }
            return response;
        }).catch(error => {
            console.error('Fetch error:', error);
            throw error;
        });
    };

    /**
     * Intercept jQuery AJAX calls if jQuery is available
     */
    if (typeof $ !== 'undefined') {
        $(document).ajaxError(function(event, xhr, settings, thrownError) {
            if (xhr.status === 403) {
                // Handle 403 Forbidden
                try {
                    const response = JSON.parse(xhr.responseText);
                    const message = response.message || "You do not have permission to perform this action.";
                    showForbiddenError(message);
                } catch (e) {
                    showForbiddenError("You do not have permission to perform this action.");
                }
                
                // Prevent default error handling
                event.preventDefault();
            }
        });
    }

    /**
     * Intercept XMLHttpRequest for other frameworks
     */
    const originalOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        this._requestURL = url;
        return originalOpen.apply(this, arguments);
    };

    const originalSend = XMLHttpRequest.prototype.send;
    XMLHttpRequest.prototype.send = function(data) {
        const xhr = this;
        
        // Wrap the onload handler
        const originalOnLoad = xhr.onload;
        xhr.onload = function() {
            if (xhr.status === 403) {
                try {
                    const response = JSON.parse(xhr.responseText);
                    const message = response.message || "You do not have permission to perform this action.";
                    showForbiddenError(message);
                } catch (e) {
                    showForbiddenError("You do not have permission to perform this action.");
                }
            }
            
            // Call original onload if it exists
            if (originalOnLoad) {
                originalOnLoad.call(xhr);
            }
        };
        
        return originalSend.apply(this, arguments);
    };

    /**
     * Public API for showing authorization errors
     */
    window.showAuthorizationError = showForbiddenError;

    console.log('Authorization error handler initialized');
})();
