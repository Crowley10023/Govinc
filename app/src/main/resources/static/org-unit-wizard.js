(function (window) {
    'use strict';

    function toId(value) {
        if (value === null || value === undefined || value === '') {
            return null;
        }
        return String(value);
    }

    function fetchOrgUnits(url) {
        return fetch(url).then(function (response) {
            if (!response.ok) {
                throw new Error('Failed to load org units');
            }
            return response.json();
        });
    }

    function createOrgUnitWizard(options) {
        var config = Object.assign({
            dataUrl: '/assessmentdetails/orgunits',
            closeOnSelect: true,
            emptyText: 'No org units available.',
            selectedId: function () { return null; },
            onSelect: function () {}
        }, options || {});

        var modal = document.getElementById(config.modalId);
        var viewport = document.getElementById(config.viewportId);
        var openButton = document.getElementById(config.openButtonId);
        var cancelButton = document.getElementById(config.cancelButtonId);

        if (!modal || !viewport || !openButton) {
            return null;
        }

        var orgUnitsCache = null;
        var orgUnitMap = {};
        var wizardStack = [];

        function closeModal() {
            modal.style.display = 'none';
            modal.setAttribute('aria-hidden', 'true');
        }

        function openModal() {
            modal.style.display = 'flex';
            modal.setAttribute('aria-hidden', 'false');
        }

        function buildOrgUnitTree(units) {
            orgUnitMap = {};
            units.forEach(function (unit) {
                orgUnitMap[unit.id] = unit;
            });
            var childIds = new Set();
            units.forEach(function (unit) {
                if (Array.isArray(unit.children)) {
                    unit.children.forEach(function (child) {
                        if (orgUnitMap[child.id]) {
                            childIds.add(child.id);
                        }
                    });
                }
            });
            return units
                .filter(function (unit) { return !childIds.has(unit.id); })
                .sort(function (a, b) { return a.name.localeCompare(b.name); });
        }

        function getWizardChildren(unit) {
            if (!Array.isArray(unit.children) || unit.children.length === 0) {
                return [];
            }
            return unit.children
                .map(function (child) { return orgUnitMap[child.id]; })
                .filter(Boolean)
                .sort(function (a, b) { return a.name.localeCompare(b.name); });
        }

        function handleSelect(unit) {
            config.onSelect(unit);
            if (config.closeOnSelect) {
                closeModal();
            }
        }

        function renderPanel(units, direction) {
            var selectedId = toId(config.selectedId());

            var panel = document.createElement('div');
            panel.classList.add('ou-wizard-panel');

            var content = document.createElement('div');
            content.classList.add('ou-wizard-content');

            var listShell = document.createElement('div');
            listShell.classList.add('ou-wizard-list-shell');

            var list = document.createElement('div');
            list.classList.add('ou-wizard-list');

            if (wizardStack.length > 1) {
                var sideNav = document.createElement('div');
                sideNav.classList.add('ou-wizard-side-nav');

                var backBtn = document.createElement('button');
                backBtn.type = 'button';
                backBtn.title = 'Go up one level';
                backBtn.classList.add('ou-wizard-nav-btn');
                backBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="9" height="13" viewBox="0 0 9 13"><polygon points="8,1 1,6.5 8,12" fill="currentColor"/></svg>';
                backBtn.addEventListener('click', function () {
                    wizardStack.pop();
                    renderPanel(wizardStack[wizardStack.length - 1], 'back');
                });

                sideNav.appendChild(backBtn);
                content.appendChild(sideNav);
            }

            if (!units || units.length === 0) {
                var empty = document.createElement('div');
                empty.classList.add('ou-wizard-empty');
                empty.textContent = config.emptyText;
                list.appendChild(empty);
            } else {
                units.forEach(function (unit) {
                    var children = getWizardChildren(unit);

                    var row = document.createElement('div');
                    row.classList.add('ou-wizard-row');

                    var selectBtn = document.createElement('button');
                    selectBtn.type = 'button';
                    selectBtn.classList.add('ou-wizard-select-btn');
                    selectBtn.textContent = unit.name;
                    if (children.length > 0) {
                        selectBtn.classList.add('has-children');
                    }
                    if (selectedId && String(unit.id) === selectedId) {
                        selectBtn.classList.add('selected');
                    }
                    selectBtn.addEventListener('click', function () {
                        handleSelect(unit);
                    });
                    row.appendChild(selectBtn);

                    if (children.length > 0) {
                        var drillBtn = document.createElement('button');
                        drillBtn.type = 'button';
                        drillBtn.title = 'Show sub-units';
                        drillBtn.classList.add('ou-wizard-nav-btn', 'drill-btn');
                        drillBtn.innerHTML = '<svg xmlns="http://www.w3.org/2000/svg" width="9" height="13" viewBox="0 0 9 13"><polygon points="1,1 8,6.5 1,12" fill="currentColor"/></svg>';
                        drillBtn.addEventListener('click', function () {
                            wizardStack.push(children);
                            renderPanel(children, 'forward');
                        });
                        row.appendChild(drillBtn);
                    }

                    list.appendChild(row);
                });
            }

            listShell.appendChild(list);
            content.appendChild(listShell);
            panel.appendChild(content);

            var existing = viewport.querySelector('.ou-wizard-panel');
            if (!direction || !existing) {
                viewport.innerHTML = '';
                viewport.appendChild(panel);
                return;
            }

            var enterFrom = direction === 'forward' ? '100%' : '-100%';
            var exitTo = direction === 'forward' ? '-100%' : '100%';
            panel.style.transform = 'translateX(' + enterFrom + ')';
            viewport.appendChild(panel);
            requestAnimationFrame(function () {
                requestAnimationFrame(function () {
                    panel.style.transition = 'transform 0.24s cubic-bezier(.4,0,.2,1)';
                    panel.style.transform = 'translateX(0)';
                    existing.style.transition = 'transform 0.24s cubic-bezier(.4,0,.2,1)';
                    existing.style.transform = 'translateX(' + exitTo + ')';
                    existing.addEventListener('transitionend', function () {
                        existing.remove();
                    }, { once: true });
                });
            });
        }

        function openWizard() {
            if (!Array.isArray(orgUnitsCache) || orgUnitsCache.length === 0) {
                return;
            }
            var topLevel = buildOrgUnitTree(orgUnitsCache);
            wizardStack = [topLevel];
            viewport.innerHTML = '';
            renderPanel(topLevel, null);
            openModal();
        }

        function ensureAndOpen() {
            if (Array.isArray(orgUnitsCache)) {
                openWizard();
                return;
            }
            fetchOrgUnits(config.dataUrl)
                .then(function (units) {
                    orgUnitsCache = Array.isArray(units) ? units : [];
                    if (orgUnitsCache.length > 0) {
                        openWizard();
                    }
                })
                .catch(function () {
                    orgUnitsCache = [];
                });
        }

        openButton.addEventListener('click', ensureAndOpen);

        if (cancelButton) {
            cancelButton.addEventListener('click', closeModal);
        }

        modal.addEventListener('click', function (event) {
            if (event.target === modal) {
                closeModal();
            }
        });

        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape' && modal.style.display === 'flex') {
                closeModal();
            }
        });

        return {
            refresh: function () {
                orgUnitsCache = null;
            },
            open: ensureAndOpen,
            close: closeModal
        };
    }

    window.createOrgUnitWizard = createOrgUnitWizard;
})(window);
