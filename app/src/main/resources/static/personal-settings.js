(function () {
    var dialog = document.getElementById('personalSettingsDialog');
    if (!dialog) return;

    var open = document.getElementById('personalSettingsOpen');
    var close = document.getElementById('personalSettingsClose');
    var form = document.getElementById('personalSettingsForm');
    var checkbox = document.getElementById('mailForNewTasks');
    var status = document.getElementById('personalSettingsStatus');
    var savedValue = false;
    var pendingSave = Promise.resolve();
    var openVersion = 0;

    open.addEventListener('click', function () {
        dialog.showModal();
        var version = ++openVersion;
        checkbox.disabled = true;
        status.textContent = '';
        pendingSave.then(function () {
            return fetch('/personal-settings', { headers: { 'Accept': 'application/json' } });
        })
            .then(function (response) {
                if (!response.ok || !response.headers.get('content-type').includes('application/json')) throw new Error();
                return response.json();
            })
            .then(function (settings) {
                if (version !== openVersion) return;
                savedValue = settings.mailForNewTasks;
                checkbox.checked = savedValue;
                checkbox.disabled = false;
            })
            .catch(function () {
                if (version === openVersion) status.textContent = 'Could not load settings. Please try again.';
            });
    });

    close.addEventListener('click', function () { dialog.close(); });
    checkbox.addEventListener('change', function () {
        var version = openVersion;
        var newValue = checkbox.checked;
        checkbox.disabled = true;
        status.textContent = 'Saving...';
        var headers = { 'Content-Type': 'application/json', 'Accept': 'application/json' };
        headers[form.dataset.csrfHeader] = form.dataset.csrfToken;
        pendingSave = fetch('/personal-settings', {
            method: 'POST',
            headers: headers,
            body: JSON.stringify({ mailForNewTasks: newValue })
        }).then(function (response) {
            if (!response.ok || !response.headers.get('content-type').includes('application/json')) throw new Error();
            savedValue = newValue;
            if (version === openVersion) status.textContent = 'Settings saved.';
        }).catch(function () {
            if (version === openVersion) {
                checkbox.checked = savedValue;
                status.textContent = 'Could not save settings. Please try again.';
            }
        }).finally(function () {
            if (version === openVersion) checkbox.disabled = false;
        });
    });
}());