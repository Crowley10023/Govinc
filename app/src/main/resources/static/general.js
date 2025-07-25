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
  if (window.jQuery) {
    var debounceTimers = {};
    $(document).on('input', '.comment-textarea', function () {
      var textarea = $(this);
      var controlId = textarea.data('control-id');
      var comment = textarea.val();
      var assessmentId = window.assessmentId;
      var feedback = textarea.nextAll('.comment-feedback').first();
      if (textarea.prop('disabled')) return;
      // Clear prior timer
      if (debounceTimers[controlId]) clearTimeout(debounceTimers[controlId]);
      debounceTimers[controlId] = setTimeout(function() {
        // AJAX save comment
        $.ajax({
          url: '/assessment/' + assessmentId + '/control/' + controlId + '/comment',
          type: 'PUT',
          contentType: 'application/json',
          data: JSON.stringify({ comment: comment }),
          success: function () {
            textarea.css('background', '#d8ffd8');
            feedback.html('<span style="color:#228B22;font-weight:bold;">Saved</span>');
            setTimeout(function () {
              feedback.empty();
              textarea.css('background', '');
            }, 1400);
          },
          error: function () {
            textarea.css('background', '#ffd8d8');
            feedback.html('<span style="color:red;font-weight:bold;">Error saving</span>');
          }
        });
      }, 650);
    });
  } else {
    console.warn('jQuery is required for assessment comment autosave.');
  }

  // ========== Collapse all security domain controls on page load ==========
  document.querySelectorAll('.domain-controls').forEach(function(ctrlDiv) {
    ctrlDiv.style.display = 'none';
  });

});
