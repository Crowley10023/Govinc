// Central JS file for navigation and general scripts

// Dropdown functionality for navigation

document.addEventListener('DOMContentLoaded', function() {
  // Dropdowns - nav
  var dropdowns = document.querySelectorAll('.dropdown');
  dropdowns.forEach(function(dropdown) {
    var button = dropdown.querySelector('.dropdown-button');
    if(button) {
      button.addEventListener('click', function(e) {
        e.stopPropagation();
        closeAllDropdowns(dropdown);
        var content = dropdown.querySelector('.dropdown-content');
        if(content) {
          content.style.display = (content.style.display === 'block') ? 'none' : 'block';
        }
      });
    }
  });

  // Close dropdowns if we click outside
  document.addEventListener('click', function() {
    closeAllDropdowns();
  });

  function closeAllDropdowns(except) {
    document.querySelectorAll('.dropdown-content').forEach(function(content) {
      if(!except || (except && !except.contains(content))) {
        content.style.display = 'none';
      }
    });
  }

  // ================== AUTOSAVE ASSESSMENT COMMENT LOGIC =====================
  window._commentSaveTimers = {};
  document.addEventListener('input', function(e) {
    if (e.target.classList.contains('comment-textarea')) {
      var textarea = e.target;
      var controlId = textarea.dataset.controlId;
      var comment = textarea.value;
      var assessmentId = window.assessmentId;
      var feedback = textarea.nextElementSibling;
      
      if (textarea.disabled) return;
      
      // Clear prior timer
      if (window._commentSaveTimers[controlId]) clearTimeout(window._commentSaveTimers[controlId]);
      
      window._commentSaveTimers[controlId] = setTimeout(function() {
        // Mark as "fetch in-flight" — keeps applyInlineUpdate from overwriting the textarea
        // with a stale DB value until the save round-trip is complete.
        window._commentSaveTimers[controlId] = 'saving';
        var csrfMeta = document.querySelector('meta[name="_csrf"]');
        var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        var csrfToken = csrfMeta ? csrfMeta.getAttribute('content') : '';
        var csrfHeaderName = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';
        var headers = { 'Content-Type': 'application/json' };
        headers[csrfHeaderName] = csrfToken;
        // Fetch API call to save comment
        fetch('/assessment/' + assessmentId + '/control/' + controlId + '/comment', {
          method: 'PUT',
          headers: headers,
          body: JSON.stringify({ comment: comment })
        })
        .then(function(response) {
          delete window._commentSaveTimers[controlId];
          if (response.ok) {
            textarea.style.background = '#d8ffd8';
            setTimeout(function() {
              textarea.style.background = '';
            }, 1400);
          } else {
            throw new Error('Failed to save');
          }
        })
        .catch(function() {
          delete window._commentSaveTimers[controlId];
          textarea.style.background = '#ffd8d8';
          if (feedback && feedback.classList.contains('comment-feedback')) {
            feedback.innerHTML = '<span style="color:red;font-weight:bold;">Error saving</span>';
          }
        });
      }, 650);
    }
  });

  // ========== Collapse all security domain controls on page load ==========
  document.querySelectorAll('.domain-controls').forEach(function(ctrlDiv) {
    ctrlDiv.style.display = 'none';
  });

});
