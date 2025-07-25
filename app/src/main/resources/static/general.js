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
});
