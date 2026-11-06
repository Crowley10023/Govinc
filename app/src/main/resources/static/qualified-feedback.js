document.addEventListener('DOMContentLoaded', function() {
    document.querySelectorAll('.qualified-feedback-select:not(:disabled)').forEach(function(select) {
        var previous = select.value;
        select.addEventListener('change', function() {
            var value = select.value;
            var token = document.querySelector('meta[name="_csrf"]');
            var header = document.querySelector('meta[name="_csrf_header"]');
            var headers = { 'Content-Type': 'application/json' };
            if (token && header) headers[header.content] = token.content;
            select.disabled = true;
            fetch('/assessment/' + window.assessmentId + '/control/' + select.dataset.controlId + '/qualified-feedback', {
                method: 'PUT', headers: headers, body: JSON.stringify({ rating: value })
            }).then(function(response) {
                if (!response.ok) throw new Error('Save failed');
                previous = value;
                if (typeof applyFilters === 'function') applyFilters();
                var stepFilter = document.getElementById('step-feedback-filter');
                if (stepFilter) stepFilter.dispatchEvent(new Event('change'));
            }).catch(function() {
                select.value = previous;
                alert('Could not save qualified feedback.');
            }).finally(function() { select.disabled = false; });
        });
    });
});