// =================== CSRF Protection ===================
$(function () {
    var csrfToken = $('meta[name="_csrf"]').attr('content');
    var csrfHeader = $('meta[name="_csrf_header"]').attr('content');
    if (csrfToken && csrfHeader) {
        $(document).ajaxSend(function (e, xhr, options) {
            xhr.setRequestHeader(csrfHeader, csrfToken);
        });
    }
});

function isRowNotApplicable(row) {
    return row && row.getAttribute('data-not-applicable') === 'true';
}

function captureBaseDisabledState(row) {
    if (!row) return;
    var elements = row.querySelectorAll('.answer-select, .comment-textarea, .answering-guide-btn, .override-slider-checkbox');
    elements.forEach(function(el) {
        if (!el.hasAttribute('data-base-disabled')) {
            el.setAttribute('data-base-disabled', el.disabled ? 'true' : 'false');
        }
    });
}

function setRowNotApplicableState(row, isNotApplicable) {
    if (!row) return;
    captureBaseDisabledState(row);
    row.setAttribute('data-not-applicable', isNotApplicable ? 'true' : 'false');
    row.classList.toggle('na-control-row', !!isNotApplicable);

    var select = row.querySelector('.answer-select');
    var comment = row.querySelector('.comment-textarea');
    var guideBtn = row.querySelector('.answering-guide-btn');
    var overrideCheckbox = row.querySelector('.override-slider-checkbox');

    if (isNotApplicable && select) {
        select.value = '';
        row.setAttribute('data-selected-answer', '');
    }

    if (select) {
        var selectBaseDisabled = select.getAttribute('data-base-disabled') === 'true';
        select.disabled = !!isNotApplicable || selectBaseDisabled;
    }
    if (comment) {
        var commentBaseDisabled = comment.getAttribute('data-base-disabled') === 'true';
        comment.disabled = !!isNotApplicable || commentBaseDisabled;
    }
    if (guideBtn) {
        var guideBaseDisabled = guideBtn.getAttribute('data-base-disabled') === 'true';
        guideBtn.disabled = !!isNotApplicable || guideBaseDisabled;
    }
    if (overrideCheckbox) {
        var overrideBaseDisabled = overrideCheckbox.getAttribute('data-base-disabled') === 'true';
        overrideCheckbox.disabled = !!isNotApplicable || overrideBaseDisabled;
    }
}

function persistNotApplicable(controlId, assessmentId, isNotApplicable) {
    var csrfMeta = document.querySelector('meta[name="_csrf"]');
    var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
    var csrfToken = csrfMeta ? csrfMeta.getAttribute('content') : '';
    var csrfHeaderName = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';
    var headers = {
        'Content-Type': 'application/json'
    };
    if (csrfToken) {
        headers[csrfHeaderName] = csrfToken;
    }

    return fetch('/assessment/' + assessmentId + '/control/' + controlId + '/not-applicable', {
        method: 'POST',
        headers: headers,
        body: JSON.stringify({ notApplicable: !!isNotApplicable })
    }).then(function(response) {
        if (!response.ok) {
            throw new Error('Failed to update Not Applicable state');
        }
        return response.json();
    });
}

// =================== Override Functions ===================
// Toggle override function - handles slider toggle
function toggleOverride(checkbox) {
    var controlId = checkbox.getAttribute('data-control-id');
    var assessmentId = checkbox.getAttribute('data-assessment-id');
    var isChecked = checkbox.checked;
    console.debug('[toggleOverride] controlId=', controlId, 'assessmentId=', assessmentId, 'checked=', isChecked);

    if (isChecked) {
        // Enable override - apply first available answer
        var $dropdown = $('select[data-control-id="' + controlId + '"]');
        var options = [];
        $dropdown.find('option').each(function() {
            var val = $(this).val();
            var text = $(this).text();
            if (val && text !== '-- select an answer --') {
                options.push({ id: val, text: text });
            }
        });

        if (options.length === 0) {
            checkbox.checked = false;
            return;
        }

        var answerId = options[0].id;
        console.debug('[toggleOverride] sending override POST', { controlId: controlId, answerId: answerId });

        $.ajax({
            url: '/assessment/' + assessmentId + '/answer-override',
            type: 'POST',
            data: { controlId: controlId, answerId: answerId },
            success: function(resp) {
                console.debug('[toggleOverride] success', resp);
                updateControlAfterOverride(controlId, answerId);
            },
            error: function(xhr) {
                console.error('[toggleOverride] error', xhr);
                alert('Could not override answer. Please try again.');
                checkbox.checked = false;
            }
        });
    } else {
        // Disable override - revert to org service answer
        console.debug('[toggleOverride] sending remove-override POST', { controlId: controlId });
        $.ajax({
            url: '/assessment/' + assessmentId + '/control/' + controlId + '/remove-override',
            type: 'POST',
            success: function(resp) {
                console.debug('[toggleOverride] remove-override success', resp);
                updateControlAfterRemoveOverride(controlId, assessmentId);
            },
            error: function(xhr) {
                console.error('[toggleOverride] remove-override error', xhr);
                alert('Could not revert to org service answer. Please try again.');
                checkbox.checked = true;
            }
        });
    }
}

// Update UI after override - get updated state from backend and update UI
function updateControlAfterOverride(controlId, answerId) {
    var assessmentId = window.assessmentId || 0;
    
    // Fetch the current state for this control
    $.ajax({
        url: '/assessment/' + assessmentId + '/control/' + controlId + '/state',
        type: 'GET',
        dataType: 'json',
        success: function(state) {
            // Update the dropdown
            var $dropdown = $('select[data-control-id="' + controlId + '"]');
            $dropdown.val(answerId);
            
            // Find the control row in the DOM
            var $controlRow = $dropdown.closest('tr');
            if ($controlRow.length === 0) return;
            
            // Get the slider checkbox and ensure it's checked
            var $sliderCheckbox = $controlRow.find('.override-slider-checkbox[data-control-id="' + controlId + '"]');
            if ($sliderCheckbox.length > 0 && !$sliderCheckbox.prop('checked')) {
                $sliderCheckbox.prop('checked', true);
            }
            
            // Keep showing the org service name even when override is active
            var $takenOverLabels = $controlRow.find('.takenOver-row span.taken-over-label');
            if (state.orgServiceName && $takenOverLabels.length > 0) {
                $takenOverLabels.text(state.orgServiceName);
            }
            
            // Enable the dropdown again
            $dropdown.prop('disabled', false);
            $dropdown.attr('data-base-disabled', 'false');
            $dropdown.removeClass('taken-over');
            
            // Enable comment textarea
            var $textarea = $controlRow.find('textarea[data-control-id="' + controlId + '"]');
            if ($textarea.length > 0) {
                $textarea.prop('disabled', false);
                $textarea.attr('data-base-disabled', 'false');
                $textarea.prop('placeholder', 'Add comment (optional)');
            }
            
            // Enable answering guide button
            var $guideBtn = $controlRow.find('.answering-guide-btn[data-control-id="' + controlId + '"]');
            if ($guideBtn.length > 0) {
                $guideBtn.prop('disabled', false);
                $guideBtn.attr('data-base-disabled', 'false');
                $guideBtn.prop('title', 'Get AI-powered answering guide');
            }
        },
        error: function() {
            // Silently continue - UI is already updated locally
        }
    });
}

// Update UI after removing override - restore org service answer
function updateControlAfterRemoveOverride(controlId, assessmentId) {
    // Fetch the current state for this control
    $.ajax({
        url: '/assessment/' + assessmentId + '/control/' + controlId + '/state',
        type: 'GET',
        dataType: 'json',
        success: function(state) {
            var $dropdown = $('select[data-control-id="' + controlId + '"]');
            var $controlRow = $dropdown.closest('tr');
            if ($controlRow.length === 0) return;
            
            // Set the org service answer
            if (state.orgServiceAnswerId) {
                $dropdown.val(state.orgServiceAnswerId);
            } else {
                $dropdown.val('');
            }
            
            // Get the slider checkbox and ensure it's unchecked
            var $sliderCheckbox = $controlRow.find('.override-slider-checkbox[data-control-id="' + controlId + '"]');
            if ($sliderCheckbox.length > 0 && $sliderCheckbox.prop('checked')) {
                $sliderCheckbox.prop('checked', false);
            }
            
            // Show the org service name label
            var $takenOverLabels = $controlRow.find('.takenOver-row span.taken-over-label');
            if (state.orgServiceName && $takenOverLabels.length > 0) {
                $takenOverLabels.first().empty();
                $takenOverLabels.first().text(state.orgServiceName);
            }
            
            // Disable the dropdown
            $dropdown.prop('disabled', true);
            $dropdown.attr('data-base-disabled', 'true');
            $dropdown.addClass('taken-over');
            
            // Restore org service comment in textarea
            var $textarea = $controlRow.find('textarea[data-control-id="' + controlId + '"]');
            if ($textarea.length > 0) {
                // Clear the current comment and restore org service comment
                if (state.orgServiceComment) {
                    $textarea.val(state.orgServiceComment);
                } else {
                    $textarea.val('');
                }
                $textarea.prop('disabled', true);
                $textarea.attr('data-base-disabled', 'true');
                $textarea.prop('placeholder', 'No comment from Org Service');
            }
            
            // Disable answering guide button
            var $guideBtn = $controlRow.find('.answering-guide-btn[data-control-id="' + controlId + '"]');
            if ($guideBtn.length > 0) {
                $guideBtn.prop('disabled', true);
                $guideBtn.attr('data-base-disabled', 'true');
                $guideBtn.prop('title', 'Disabled for taken-over answers');
            }
        },
        error: function() {
            // Silently continue - UI is already updated locally
        }
    });
}

// =================== Modal Logic and Dynamic Content Loading ===================
$(document).ready(function () {
    function renderUserPills(users) {
        if (!Array.isArray(users) || users.length === 0) {
            return '<span class="text-muted">None</span>';
        }

        var html = '<div class="pill-list">';
        users.forEach(function (user) {
            html += $('<span/>').addClass('user-pill').text(user.name || '').prop('outerHTML');
        });
        html += '</div>';
        return html;
    }

    // Org Unit modal logic (shared wizard)
    if (window.createOrgUnitWizard && document.getElementById('choose-orgunit-btn')) {
        var hiddenInput = document.getElementById('orgunit-hidden');
        var selectedLabel = document.getElementById('orgunit-selected-label');

        hiddenInput.value = window.currentOrgUnitId || '';

        window.createOrgUnitWizard({
            modalId: 'orgunit-modal-bg',
            viewportId: 'orgunitWizardViewport',
            openButtonId: 'choose-orgunit-btn',
            cancelButtonId: 'orgunit-cancel-btn',
            closeOnSelect: true,
            selectedId: function () {
                return hiddenInput.value || window.currentOrgUnitId || '';
            },
            onSelect: function (unit) {
                hiddenInput.value = String(unit.id);
                selectedLabel.textContent = unit.name;
                document.getElementById('orgunit-form').submit();
            }
        });
    }

    // User modal logic
    $('#choose-users-btn').click(function () {
        var $modal = $('#users-modal-bg');
        var $error = $('#users-modal-error');
        var assessmentId = window.assessmentId || 0;
        var addAssessorMode = !!window.assessmentAddAssessorMode;
        var usersEndpoint = addAssessorMode
            ? ('/assessment/' + assessmentId + '/assessors')
            : '/users/api';
        $error.hide();
        $modal.css('display', 'flex');

        // Load users via AJAX
        var $select = $('#users-multiselect');
        $select.prop('disabled', true);
        $select.html('<option>Loading...</option>');

        // Get assigned user IDs from Thymeleaf/JS variable
        var assignedUserIds = window.assessmentAssignedUserIds || [];
        $.getJSON(usersEndpoint, function (users) {
            $select.empty();
            if (!$.isArray(users) || users.length === 0) {
                $select.append($('<option>').val('').text(addAssessorMode ? '(No assessors found)' : '(No users found)'));
            } else {
                if (addAssessorMode) {
                    var assignedSet = new Set((assignedUserIds || []).map(function (id) { return String(id); }));
                    var usersToAdd = users.filter(function (user) {
                        return !assignedSet.has(String(user.id));
                    });

                    if (usersToAdd.length === 0) {
                        $select.append($('<option>').val('').text('(All assessors are already added)'));
                    } else {
                        usersToAdd.forEach(function (user) {
                            var $opt = $('<option>').val(user.id).text(user.name + ' (' + user.email + ')');
                            $select.append($opt);
                        });
                    }
                } else {
                    users.forEach(function (user) {
                        var $opt = $('<option>').val(user.id).text(user.name + ' (' + user.email + ')');
                        if (assignedUserIds.includes(user.id) || assignedUserIds.includes(user.id.toString())) {
                            $opt.prop('selected', true);
                        }
                        $select.append($opt);
                    });
                }
            }
            $select.prop('disabled', false);
        }).fail(function () {
            $select.html('<option value="">' + (addAssessorMode ? '(Could not load assessors)' : '(Could not load users)') + '</option>');
        });
    });

    $('#users-modal-cancel-btn').click(function () {
        $('#users-modal-bg').hide();
    });

    $('#users-modal-bg').on('mousedown', function (event) {
        if (event.target === this) { $('#users-modal-bg').hide(); }
    });

    $('#choose-compliance-check-btn').click(function () {
        var $modal = $('#compliance-check-modal-bg');
        var $select = $('#compliance-check-select');
        var $error = $('#compliance-check-modal-error');
        var currentComplianceCheckId = window.currentComplianceCheckId;
        var securityCatalogId = window.securityCatalogId || 0;

        $error.hide().text('');
        $select.prop('disabled', true).html('<option value="">Loading...</option>');
        $modal.css('display', 'flex');

        $.getJSON('/assessment/compliance-checks?catalogId=' + encodeURIComponent(securityCatalogId), function (checks) {
            $select.empty();
            $select.append($('<option>').val('').text('-- None --'));
            if (Array.isArray(checks)) {
                checks.forEach(function (check) {
                    var label = check.name || '';
                    if (check.description) {
                        label += ' - ' + check.description;
                    }
                    var $option = $('<option>').val(check.id).text(label);
                    if (currentComplianceCheckId != null && String(check.id) === String(currentComplianceCheckId)) {
                        $option.prop('selected', true);
                    }
                    $select.append($option);
                });
            }
            $select.prop('disabled', false);
        }).fail(function () {
            $select.html('<option value="">(Could not load compliance checks)</option>').prop('disabled', true);
            $error.show().text('Could not load compliance checks.');
        });
    });

    $('#compliance-check-cancel-btn').click(function () {
        $('#compliance-check-modal-bg').hide();
    });

    $('#compliance-check-modal-bg').on('mousedown', function (event) {
        if (event.target === this) {
            $('#compliance-check-modal-bg').hide();
        }
    });

    $('#compliance-check-form').submit(function (event) {
        event.preventDefault();
        var assessmentId = window.assessmentId || 0;
        var selectedValue = $('#compliance-check-select').val();
        var selectedId = selectedValue ? Number(selectedValue) : null;

        $('#compliance-check-save-btn').prop('disabled', true);
        $('#compliance-check-modal-error').hide().text('');

        $.ajax({
            url: '/assessment/' + assessmentId + '/compliance-check',
            type: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify({ complianceCheckId: selectedId }),
            success: function (response) {
                window.currentComplianceCheckId = response && response.id != null ? response.id : null;
                $('#assessment-compliance-check-name').text(response && response.name ? response.name : 'None');
                $('#compliance-check-modal-bg').hide();
                window.location.reload();
            },
            error: function (xhr) {
                var message = 'Could not update the compliance check.';
                if (xhr.responseJSON && xhr.responseJSON.message) {
                    message += ' ' + xhr.responseJSON.message;
                }
                $('#compliance-check-modal-error').show().text(message);
                $('#compliance-check-save-btn').prop('disabled', false);
            }
        });
    });

    // AJAX Save users selection
    $('#users-modal-form').submit(function (event) {
        event.preventDefault();
        var $select = $('#users-multiselect');
        var selected = $select.val() || [];
        var assessmentId = window.assessmentId || 0;
        var addAssessorMode = !!window.assessmentAddAssessorMode;
        var updateEndpoint = addAssessorMode
            ? ('/assessment/' + assessmentId + '/assessors')
            : ('/assessment/' + assessmentId + '/users');

        if (addAssessorMode && selected.length === 0) {
            $('#users-modal-error').show().text('Please select at least one user to add.');
            return;
        }

        $('#users-modal-save-btn').prop('disabled', true);
        $('#users-modal-error').hide();
        $.ajax({
            url: updateEndpoint,
            type: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify(selected),
            success: function (updatedUsers) {
                // Update users in the table
                $('#assessment-users-cell').html(renderUserPills(updatedUsers));
                $('#users-modal-bg').hide();
                $('#users-modal-save-btn').prop('disabled', false);
                $('#users-modal-error').hide();
                // Keep assigned user ids in sync so reopening preselects correctly.
                window.assessmentAssignedUserIds = (updatedUsers || []).map(function (u) { return u.id; });
            },
            error: function (xhr) {
                let msg = "Could not update users.";
                if (xhr.responseJSON && xhr.responseJSON.message) msg += " " + xhr.responseJSON.message;
                $('#users-modal-error').show().text(msg);
                $('#users-modal-save-btn').prop('disabled', false);
            }
        });
    });

    // Org Service modal logic - open, fetch, display
    $('#choose-orgservices-btn').click(function () {
        var assessmentId = window.assessmentId || 0;
        $('#orgservice-modal').css('display', 'flex');
        $('#orgservice-list').html('<em>Loading...</em>');
        
        $.getJSON('/assessment/all-orgservices', function (allServices) {
            $.getJSON('/assessment/' + assessmentId + '/orgservice-ids', function (assignedIds) {
                var html = '';
                if (Array.isArray(allServices) && allServices.length > 0) {
                    allServices.forEach(function (os) {
                        var checked = Array.isArray(assignedIds) && assignedIds.map(String).indexOf(String(os.id)) !== -1 ? 'checked' : '';
                        html += '<label class="orgservice-label">' +
                            '<input type="checkbox" name="orgServiceIds" value="' + os.id + '" ' + checked + '> ' +
                            $('<span/>').text(os.name).prop('outerHTML') +
                            '</label>';
                    });
                } else {
                    html = '<em>No Org Services available.</em>';
                }
                $('#orgservice-list').html(html);
            }).fail(function () {
                $('#orgservice-list').html('<span style="color:#c22">Could not load assigned Org Services.</span>');
            });
        }).fail(function () {
            $('#orgservice-list').html('<span style="color:#c22">Could not load Org Services.</span>');
        });
    });

    // Modal Cancel for Org Service
    $('#orgservice-modal-cancel').click(function () {
        $('#orgservice-modal').hide();
    });

    $('#orgservice-modal').on('mousedown', function (event) {
        if (event.target === this) $('#orgservice-modal').hide();
    });

    // Modal Save for Org Service
    $('#orgservice-form').submit(function (event) {
        event.preventDefault();
        var assessmentId = window.assessmentId || 0;
        var selected = [];
        $('#orgservice-list input[name="orgServiceIds"]:checked').each(function () {
            selected.push(Number($(this).val()));
        });
        $('#orgservice-modal-save').prop('disabled', true);
        $.ajax({
            url: '/assessment/' + assessmentId + '/orgservices',
            type: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify(selected),
            success: function () {
                location.reload();
            },
            error: function () {
                alert('Could not update Org Services!');
                $('#orgservice-modal-save').prop('disabled', false);
            }
        });
    });

    // =================== Answering Guide Modal Logic ===================
    $(document).on('click', '.answering-guide-btn', function() {
        var controlId = $(this).data('control-id');
        var controlName = $(this).data('control-name');
        var controlDetail = $(this).data('control-detail');
        var securityCatalogId = window.securityCatalogId || 0;
        
        openAnsweringGuideModal(controlId, controlName, controlDetail, securityCatalogId);
    });

    // Create direct URL for assessment
    $("#create-url-btn").click(function () {
        let assessmentId = window.assessmentId || 0;
        $("#create-url-btn").prop('disabled', true);
        $.ajax({
            url: '/assessment/' + assessmentId + '/create-url',
            type: 'POST',
            success: function (data) {
                $("#create-url-btn").prop('disabled', false);
                if (data && data.directUrl) {
                    var fullUrl = window.location.origin + data.directUrl;
                    $('#create-url-modal-link').attr('href', data.directUrl).text(fullUrl);
                    $('#create-url-modal-expiry').text(data.expirationDate || '');
                    $('#create-url-modal').css('display', 'flex');
                    // Also update the Assessment URL cell in the details table
                    var $urlTd = $("th:contains('Assessment URL')").next('td');
                    if ($urlTd.length > 0) {
                        var anchor = $urlTd.find('a');
                        if (anchor.length > 0) {
                            anchor.attr('href', data.directUrl);
                            anchor.text(fullUrl);
                            anchor.parent().show();
                            anchor.parent().siblings('span').hide();
                        } else {
                            $urlTd.html('<span><a href="' + data.directUrl + '" target="_blank">' + $('<span/>').text(fullUrl).html() + '</a></span>');
                        }
                    }
                }
            },
            error: function () {
                $("#create-url-btn").prop('disabled', false);
                alert('Error creating URL. Please try again.');
            }
        });
    });

    $('#create-url-modal-close').click(function () {
        $('#create-url-modal').css('display', 'none');
    });

    // Save answer on dropdown change (delegated handler to support dynamic elements)
    $(document).on('change', '.answer-select', function () {
        try {
            var select = $(this);
            var controlId = select.data('control-id');
            var answerId = select.val();

            // Debug: ensure handler runs
            console.debug('[answer-select change] controlId=', controlId, 'answerId=', answerId);

            // Find or create feedback icon nearby (robust across templates)
            var $dropdownRow = select.closest('.dropdown-row');
            var $feedbackIcon = $dropdownRow.find('.answer-feedback');
            if ($feedbackIcon.length === 0) {
                // Insert a feedback span right after the select to ensure UI feedback works
                $feedbackIcon = $('<span class="answer-feedback"></span>');
                select.after($feedbackIcon);
            }
            $feedbackIcon.empty();
            select.css('background', '');

            if (!answerId) {
                $feedbackIcon.empty();
                return;
            }
            let assessmentId = window.assessmentId || 0;
            if (assessmentId && controlId) {
                // Include override flag if present for this control row
                var $row = select.closest('tr');
                var isOverride = false;
                if ($row.length > 0) {
                    var $ov = $row.find('.override-slider-checkbox[data-control-id="' + controlId + '"]');
                    if ($ov.length > 0) {
                        isOverride = !!$ov.prop('checked');
                    }
                }

                console.debug('[answer-select change] assessmentId=', assessmentId, 'isOverride=', isOverride);

                // Send AJAX (jQuery will URL-encode boolean to 'true'/'false')
                console.debug('[answer-save] sending POST', { url: '/assessment/' + assessmentId + '/answer', controlId: controlId, answerId: answerId, isOverride: isOverride });
                $.ajax({
                    url: '/assessment/' + assessmentId + '/answer',
                    type: 'POST',
                    traditional: true,
                    contentType: 'application/x-www-form-urlencoded; charset=UTF-8',
                    data: { controlId: controlId, answerId: answerId, isOverride: isOverride },
                    success: function (data, textStatus, jqXHR) {
                        console.debug('[answer-save success]', {data: data, textStatus: textStatus});

                        // Update UI
                        select.css('background', '#d8ffd8');
                        $feedbackIcon.html('<span class="answer-success" title="Saved"><svg viewBox="0 0 18 18"><circle cx="9" cy="9" r="8" fill="#d8ffd8" stroke="#33aa33" stroke-width="2"/><path d="M5 9l3 3 5-5" fill="none" stroke="#33aa33" stroke-width="2"/></svg></span>');
                        setTimeout(function () {
                            $feedbackIcon.empty();
                            select.css('background', '');
                        }, 1500);

                        // Update the row's data-selected-answer attribute (used by charts & filters)
                        try {
                            var selectedText = (select.find('option:selected').text() || '').trim();
                            var $tr = select.closest('tr');
                            if ($tr.length > 0) {
                                $tr.attr('data-selected-answer', selectedText);
                            }

                            // If override flag was sent, ensure checkbox state reflects it
                            if (isOverride) {
                                var $ovcb = $tr.find('.override-slider-checkbox[data-control-id="' + controlId + '"]');
                                if ($ovcb.length > 0 && !$ovcb.prop('checked')) {
                                    $ovcb.prop('checked', true);
                                }
                            }

                            // Recompute completion and charts
                            updateAnsweredCount();
                            debouncedUpdateMaturityChart();
                            debouncedUpdatePieChart();
                            checkDomainCompleteness();
                        } catch (e) {
                            console.debug('Could not update local DOM state after save', e);
                        }
                    },
                    error: function (jqXHR, textStatus, errorThrown) {
                        console.error('[answer-save error]', {jqXHR: jqXHR, textStatus: textStatus, errorThrown: errorThrown});
                        select.css('background', '#ffd8d8');
                        var msg = 'Error saving answer.';
                        try {
                            if (jqXHR && jqXHR.responseText) msg = jqXHR.responseText;
                        } catch (e) {}
                        if ((msg || '').trim().toLowerCase() === 'not_applicable') {
                            var $trNa = select.closest('tr');
                            if ($trNa.length > 0) {
                                setRowNotApplicableState($trNa.get(0), true);
                                var naCheckbox = $trNa.find('.not-applicable-checkbox');
                                if (naCheckbox.length > 0) {
                                    naCheckbox.prop('checked', true);
                                }
                                updateAnsweredCount();
                                checkDomainCompleteness();
                                debouncedUpdateMaturityChart();
                                debouncedUpdatePieChart();
                                applyFilters();
                            }
                            msg = 'This control is marked as Not Applicable.';
                        }
                        $feedbackIcon.html('<span class="answer-error" title="Error saving">' + $('<span/>').text(msg).html() + '</span>');
                    }
                });
            }
        } catch (e) {
            // Prevent any unexpected errors from breaking other UI behavior
            console.error('Error in answer-select change handler', e);
        }
    });

    $(document).on('change', '.not-applicable-checkbox', function () {
        var checkbox = this;
        var controlId = checkbox.getAttribute('data-control-id');
        var assessmentId = checkbox.getAttribute('data-assessment-id');
        var isNotApplicable = checkbox.checked;
        var row = checkbox.closest('tr');

        if (!controlId || !assessmentId || !row) {
            checkbox.checked = !isNotApplicable;
            return;
        }

        setRowNotApplicableState(row, isNotApplicable);
        updateAnsweredCount();
        checkDomainCompleteness();
        debouncedUpdateMaturityChart();
        debouncedUpdatePieChart();
        applyFilters();

        persistNotApplicable(controlId, assessmentId, isNotApplicable).catch(function () {
            checkbox.checked = !isNotApplicable;
            setRowNotApplicableState(row, !isNotApplicable);
            updateAnsweredCount();
            checkDomainCompleteness();
            debouncedUpdateMaturityChart();
            debouncedUpdatePieChart();
            applyFilters();
            alert('Could not update Not Applicable state.');
        });
    });

    // Word Report Progress Handler with real-time polling
    $('#word-report-btn').click(function (e) {
        e.preventDefault();
        var href = $(this).attr('href');
        showWordReportProgress();
        setTimeout(function () {
            window.location.href = href;
        }, 100);
    });
});

// =================== Copy Assessment URL ===================
function copyAssessmentUrl() {
    var url = $('#create-url-modal-link').text().trim();
    if (!url) return;
    navigator.clipboard.writeText(url).then(function() {
        var status = document.getElementById('create-url-copy-status');
        status.style.display = 'inline';
        setTimeout(function() { status.style.display = 'none'; }, 2000);
    }).catch(function() {
        // Fallback for browsers that don't support clipboard API
        var ta = document.createElement('textarea');
        ta.value = url;
        ta.style.cssText = 'position:fixed;opacity:0;';
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        document.body.removeChild(ta);
        var status = document.getElementById('create-url-copy-status');
        status.style.display = 'inline';
        setTimeout(function() { status.style.display = 'none'; }, 2000);
    });
}

// =================== Answering Guide Functions ===================
var currentAnsweringGuideState = {
    controlId: null,
    controlName: null,
    securityCatalogId: null,
    questions: [],
    answers: {},
    proposedAnswer: null,
    comment: null
};

function _aguideShowOverlay(label) {
    var $el = $('#answering-guide-overlay');
    var $lbl = $('#answering-guide-overlay-label');
    if ($lbl.length) $lbl.text(label || 'Loading…');
    $el.addClass('active');
}
function _aguideHideOverlay() {
    $('#answering-guide-overlay').removeClass('active');
}

function openAnsweringGuideModal(controlId, controlName, controlDetail, securityCatalogId) {
    currentAnsweringGuideState.controlId = controlId;
    currentAnsweringGuideState.controlName = controlName;
    currentAnsweringGuideState.securityCatalogId = securityCatalogId;
    currentAnsweringGuideState.questions = [];
    currentAnsweringGuideState.answers = {};
    currentAnsweringGuideState.proposedAnswer = null;
    currentAnsweringGuideState.comment = null;

    $('#answering-guide-control-name').html('<h4>' + $('<span/>').text(controlName).html() + '</h4><p>' + $('<span/>').text(controlDetail).html() + '</p>');
    $('#answering-guide-questions').empty();
    $('#answering-guide-proposed-answer').hide();
    $('#answering-guide-generating').hide();
    $('#answering-guide-loading').hide();
    $('#answering-guide-modal-bg').css('display', 'flex');
    _aguideShowOverlay('Generating questions…');

    // Populate right panel with maturity answers
    populateGuideMaturityAnswers(controlId);

    // Capture now so the success handler can discard stale responses if the guide
    // was reopened for a different control while this AJAX was in-flight.
    var openedForControlId = controlId;

    // Fetch questions from backend
    $.ajax({
        url: '/assessment/generate-answering-guide-questions',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            controlId: controlId,
            controlName: controlName,
            controlDetail: controlDetail,
            securityCatalogId: securityCatalogId
        }),
        success: function(response) {
            $('#answering-guide-loading').hide();
            _aguideHideOverlay();
            // Guard against stale response from a previously opened guide
            if (currentAnsweringGuideState.controlId !== openedForControlId) return;
            if (response && response.questions && Array.isArray(response.questions)) {
                currentAnsweringGuideState.questions = response.questions;
                displayAnsweringGuideQuestions(response.questions);
            } else {
                $('#answering-guide-questions').html('<p style="color: red;">Error: Could not load questions.</p>');
            }
        },
        error: function(xhr) {
            $('#answering-guide-loading').hide();
            _aguideHideOverlay();
            var errorMsg = 'Error generating questions. Please try again.';
            if (xhr.responseJSON && xhr.responseJSON.message) {
                errorMsg = xhr.responseJSON.message;
            }
            $('#answering-guide-questions').html('<p style="color: red;">' + $('<span/>').text(errorMsg).html() + '</p>');
        }
    });
}

function displayAnsweringGuideQuestions(questions) {
    var html = '<div class="questions-container">';
    questions.forEach(function(q, index) {
        html += '<div class="question-item">';
        html += '<div class="question-label">' + (index + 1) + '. ' + $('<span/>').text(q).html() + '</div>';
        html += '<div class="question-answer-options">';
        html += '<button type="button" class="question-pill" data-question-index="' + index + '" data-value="Yes">Yes</button>';
        html += '<button type="button" class="question-pill" data-question-index="' + index + '" data-value="No">No</button>';
        html += '</div>';
        html += '</div>';
    });
    html += '<button type="button" class="guide-submit-answers-btn" onclick="submitAnsweringGuideAnswers()">Submit Answers</button>';
    html += '</div>';
    var $container = $('#answering-guide-questions');
    $container.html(html).css('display', 'flex');
    // Pill toggle handler
    $container.find('.question-pill').on('click', function() {
        var idx = $(this).data('question-index');
        $container.find('.question-pill[data-question-index="' + idx + '"]').removeClass('question-pill-selected');
        $(this).addClass('question-pill-selected');
    });
}

function submitAnsweringGuideAnswers() {
    var answers = [];
    var allAnswered = true;
    for (var i = 0; i < currentAnsweringGuideState.questions.length; i++) {
        var $selected = $('#answering-guide-questions .question-pill[data-question-index="' + i + '"].question-pill-selected');
        if ($selected.length === 0) {
            allAnswered = false;
            break;
        }
        answers.push($selected.data('value'));
    }
    
    if (!allAnswered) {
        alert('Please answer all questions before submitting.');
        return;
    }
    
    currentAnsweringGuideState.answers = answers;

    // Get maturity model answers from the assessment dropdown
    var controlId = currentAnsweringGuideState.controlId;
    var selectElement = $('select[data-control-id="' + controlId + '"]');
    var maturityModelAnswers = [];
    
    if (selectElement.length > 0) {
        selectElement.find('option').each(function() {
            var val = $(this).val();
            var text = $(this).text();
            // Skip empty option
            if (val && text !== '-- select an answer --') {
                maturityModelAnswers.push({
                    id: val,
                    answer: text,
                    rating: $(this).attr('data-rating') !== undefined ? Number($(this).attr('data-rating')) : null,
                    description: $(this).attr('data-description') || ''
                });
            }
        });
    }
    
    // Validate that we have maturity model answers
    if (maturityModelAnswers.length === 0) {
        alert('Error: No maturity model answers found for this control.');
        return;
    }

    // Show loading overlay on submit
    $('#answering-guide-questions').hide();
    $('#answering-guide-proposed-answer').hide();
    $('#answering-guide-generating').hide();
    _aguideShowOverlay('Generating answer…');

    // Capture the controlId NOW so the success callback can verify the guide
    // hasn't been reopened for a different control while this AJAX was in-flight.
    var submittedControlId = currentAnsweringGuideState.controlId;

    // Send answers to backend to get proposed answer
    $.ajax({
        url: '/assessment/generate-answer-from-guide',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            controlId: currentAnsweringGuideState.controlId,
            controlName: currentAnsweringGuideState.controlName,
            securityCatalogId: currentAnsweringGuideState.securityCatalogId,
            questions: currentAnsweringGuideState.questions,
            answers: answers,
            maturityModelAnswers: maturityModelAnswers
        }),
        success: function(response) {
            $('#answering-guide-generating').hide();
            _aguideHideOverlay();
            // Guard: if the user already opened a new guide for a different control,
            // discard this stale response to prevent state mixing.
            if (currentAnsweringGuideState.controlId !== submittedControlId) return;
            if (response && response.proposedAnswer) {
                currentAnsweringGuideState.proposedAnswer = response.proposedAnswer;
                currentAnsweringGuideState.comment = response.comment || null;
                $('#answering-guide-questions').show();
                highlightSuggestedAnswer(response.proposedAnswer);
            } else {
                alert('Error generating answer. Please try again.');
                $('#answering-guide-questions').show();
            }
        },
        error: function(xhr) {
            $('#answering-guide-generating').hide();
            _aguideHideOverlay();
            var errorMsg = 'Error generating answer. Please try again.';
            if (xhr.responseJSON && xhr.responseJSON.message) {
                errorMsg = xhr.responseJSON.message;
            }
            alert(errorMsg);
            // Show questions again on error
            $('#answering-guide-questions').show();
        }
    });
}

function takeoverProposedAnswer() {
    if (!currentAnsweringGuideState.proposedAnswer || !currentAnsweringGuideState.controlId) {
        alert('Error: No proposed answer available.');
        return;
    }

    var proposedAnswer = currentAnsweringGuideState.proposedAnswer;
    var controlId = currentAnsweringGuideState.controlId;

    // Find the matching answer option in the dropdown and set it
    var selectElement = $('select[data-control-id="' + controlId + '"]');
    if (selectElement.length > 0) {
        var matchedOption = selectElement.find('option').filter(function() {
            return $(this).text().trim() === proposedAnswer.trim();
        }).first();

        if (matchedOption.length > 0) {
            applyAnswerFromGuide(matchedOption.val(), proposedAnswer);
        } else {
            alert('Proposed answer does not match available options. Please select manually.');
        }
    } else {
        alert('Could not find answer dropdown for this control.');
    }
}

function discardProposedAnswer() {
    // Reset highlighted suggestion
    $('.maturity-answer-card').removeClass('maturity-answer-suggested');

    // Go back to questions so user can try again
    $('#answering-guide-questions').show();
    $('#answering-guide-proposed-answer').hide();
    $('#answering-guide-generating').hide();

    // Reset answer selections
    $('input[type="radio"][name^="question_"]').prop('checked', false);
    currentAnsweringGuideState.proposedAnswer = null;
}

function closeAnsweringGuideModal() {
    $('#answering-guide-modal-bg').css('display', 'none');
    _aguideHideOverlay();
    
    // Reset all modal elements for next use
    $('#answering-guide-loading').hide();
    $('#answering-guide-questions').empty();
    $('#answering-guide-proposed-answer').hide();
    $('#answering-guide-generating').hide();
    $('#guide-maturity-answers-list').empty();
    
    currentAnsweringGuideState = {
        controlId: null,
        controlName: null,
        securityCatalogId: null,
        questions: [],
        answers: {},
        proposedAnswer: null
    };
}

// Populate the right panel with maturity answers from the control's dropdown
function populateGuideMaturityAnswers(controlId) {
    var $selectElement = $('select[data-control-id="' + controlId + '"]');
    var $list = $('#guide-maturity-answers-list');
    $list.empty();

    if ($selectElement.length === 0) {
        $list.html('<p style="color:#888;font-size:0.9em;">No maturity answers available.</p>');
        return;
    }

    var items = [];
    $selectElement.find('option').each(function() {
        var val = $(this).val();
        var text = $(this).text().trim();
        var desc = $(this).attr('data-description') || '';
        if (!val || text === '-- select an answer --') return;
        items.push({ id: val, text: text, desc: desc });
    });

    if (items.length === 0) {
        $list.html('<p style="color:#888;font-size:0.9em;">No maturity answers found.</p>');
        return;
    }

    items.forEach(function(item) {
        var $card = $('<div class="maturity-answer-card"></div>')
            .attr('data-answer-id', item.id)
            .attr('data-answer-text', item.text);
        var $name = $('<div class="maturity-answer-name"></div>').text(item.text);
        $card.append($name);
        if (item.desc) {
            var $desc = $('<div class="maturity-answer-desc"></div>').text(item.desc);
            $card.append($desc);
        }
        $card.on('click', function() {
            applyAnswerFromGuide(item.id, item.text);
        });
        $list.append($card);
    });
}

// Highlight the card matching the suggested answer on the right panel
function highlightSuggestedAnswer(proposedAnswerText) {
    $('.maturity-answer-card').removeClass('maturity-answer-suggested');
    var $matched = null;
    $('.maturity-answer-card').each(function() {
        var cardText = $(this).attr('data-answer-text') || '';
        if (cardText.trim() === proposedAnswerText.trim()) {
            $(this).addClass('maturity-answer-suggested');
            $matched = $(this);
        }
    });
    // Scroll matched card into view
    if ($matched) {
        var $list = $('#guide-maturity-answers-list');
        var itemTop = $matched.position() ? $matched.position().top : 0;
        $list.scrollTop($list.scrollTop() + itemTop - 30);
    }
}

// Apply a maturity answer from the right panel or proposed answer section
function applyAnswerFromGuide(answerId, answerText) {
    var controlId = currentAnsweringGuideState.controlId;
    if (!controlId) return;
    var $select = $('select[data-control-id="' + controlId + '"]');
    if ($select.length === 0) {
        alert('Could not find answer dropdown for this control.');
        return;
    }

    var comment = currentAnsweringGuideState.comment || null;
    var capturedControlId = controlId;
    var assessmentId = window.assessmentId || 0;

    // Close modal immediately so the user sees the page update
    closeAnsweringGuideModal();

    function doApplyAnswer() {
        $select.val(answerId).change();
    }

    if (comment && assessmentId) {
        // 1. Set the comment textarea in the UI
        var $textarea = $('textarea.comment-textarea[data-control-id="' + capturedControlId + '"]');
        if ($textarea.length > 0 && !$textarea.prop('disabled')) {
            $textarea.val(comment);
            // Cancel any pending debounce timer so the immediate save wins
            if (window._commentSaveTimers && window._commentSaveTimers[capturedControlId]) {
                clearTimeout(window._commentSaveTimers[capturedControlId]);
                delete window._commentSaveTimers[capturedControlId];
            }
        }
        // 2. Save comment to server immediately, then apply the answer
        //    so the SSE broadcast triggered by the answer save already includes the comment.
        var csrfMeta = document.querySelector('meta[name="_csrf"]');
        var csrfHeaderMeta = document.querySelector('meta[name="_csrf_header"]');
        var csrfToken = csrfMeta ? csrfMeta.getAttribute('content') : '';
        var csrfHeaderName = csrfHeaderMeta ? csrfHeaderMeta.getAttribute('content') : 'X-CSRF-TOKEN';
        var headers = { 'Content-Type': 'application/json' };
        headers[csrfHeaderName] = csrfToken;
        fetch('/assessment/' + assessmentId + '/control/' + capturedControlId + '/comment', {
            method: 'PUT',
            headers: headers,
            body: JSON.stringify({ comment: comment })
        }).then(function() {
            doApplyAnswer();
        }).catch(function() {
            // Apply answer anyway even if comment save failed
            doApplyAnswer();
        });
    } else {
        doApplyAnswer();
    }
}

// =================== Collapsible Domain Logic ===================
function toggleDomain(header) {
    var controlsDiv = header.nextElementSibling;
    if (controlsDiv.style.display === 'none' || controlsDiv.style.display === '') {
        controlsDiv.style.display = 'block';
    } else {
        controlsDiv.style.display = 'none';
    }
}

// Toggle all security control domain sections (not the assessment-details card)
var _allDomainsExpanded = false;
function toggleAllDomains() {
    _allDomainsExpanded = !_allDomainsExpanded;
    // Select only domain-collapsible cards that are NOT the assessment-details card
    document.querySelectorAll('.domain-collapsible:not(.assessment-details-collapsible)').forEach(function(card) {
        var header = card.querySelector('.domain-header');
        var body = header ? header.nextElementSibling : null;
        if (!body) return;
        body.style.display = _allDomainsExpanded ? 'block' : 'none';
        var chevron = header.querySelector('.domain-chevron svg');
        if (chevron) {
            chevron.style.transform = _allDomainsExpanded ? 'rotate(180deg)' : '';
        }
    });
    var btn = document.getElementById('expand-collapse-all-btn');
    if (btn) {
        btn.textContent = _allDomainsExpanded ? '\u2195 Collapse All' : '\u2195 Expand All';
    }
}

function toggleAssessmentDetails(header) {
    var body = header.nextElementSibling;
    if (body.style.display === 'none' || body.style.display === '') {
        body.style.display = 'block';
    } else {
        body.style.display = 'none';
    }
    var chevron = header.querySelector('.domain-chevron svg');
    if (chevron) {
        if (body.style.display === 'block') {
            chevron.style.transform = 'rotate(180deg)';
        } else {
            chevron.style.transform = '';
        }
    }
}

// =================== Word Report Progress Handler ===================
function showWordReportProgress() {
    $('#word-report-progress-modal').css('display', 'flex');
    startProgressPolling();
}

function startProgressPolling() {
    const assessmentId = window.currentAssessmentIdForProgress;
    const progressPollInterval = setInterval(function () {
        $.ajax({
            url: '/assessment/' + assessmentId + '/word-report-progress',
            type: 'GET',
            dataType: 'json',
            success: function (data) {
                $('#progress-bar-fill').css('width', data.percent + '%');
                $('#progress-percentage').text(data.percent + '%');
                $('#progress-status').text(data.status);
                
                if (data.percent >= 100) {
                    clearInterval(progressPollInterval);
                    setTimeout(function () {
                        $('#word-report-progress-modal').css('display', 'none');
                        resetProgress();
                    }, 1500);
                }
            },
            error: function () {
                // Continue polling on error
            }
        });
    }, 1000);
}

function resetProgress() {
    $('#progress-bar-fill').css('width', '0%');
    $('#progress-percentage').text('0%');
    $('#progress-status').text('Initializing...');
}

// =================== Domain Completeness Check ===================
const CHECKMARK_SVG = `<svg width="22" height="22" viewBox="0 0 22 22"><circle cx="11" cy="11" r="10" stroke="#22b573" stroke-width="2" fill="#fff"/><polyline points="6,12 10,16 16,7" stroke="#22b573" stroke-width="2.5" fill="none"/></svg>`;

function checkDomainCompleteness() {
    document.querySelectorAll(".domain-checkmark").forEach(function (checkmarkSpan) {
        const domainId = checkmarkSpan.getAttribute("data-domain-id");
        const selects = document.querySelectorAll(`.answer-select[data-domain-id='${domainId}']`);
        let allAnswered = true;
        let applicableCount = 0;
        selects.forEach(select => {
            var row = select.closest('tr');
            if (isRowNotApplicable(row)) return;
            applicableCount++;
            if (!select.value) allAnswered = false;
        });
        if (applicableCount === 0) allAnswered = false;
        checkmarkSpan.innerHTML = allAnswered ? CHECKMARK_SVG : "";
    });
}

// =================== Filter and Completion Tracking ===================
let filterState = {
    showUnansweredOnly: false,
    selectedMaturityLevel: '',
    totalControls: 0,
    answeredControls: 0,
    maturityAnswers: []
};

function initializeFilterBar() {
    // Get all answer selects to determine total and answered counts
    var allSelects = document.querySelectorAll('.answer-select');
    filterState.totalControls = Array.from(allSelects).filter(function(select) {
        return !isRowNotApplicable(select.closest('tr'));
    }).length;

    // Calculate initial answered count
    updateAnsweredCount();

    // Update maturity filter options based on available answers
    populateMaturityFilter();

    // Set up event listeners
    var unansweredToggle = document.getElementById('filter-unanswered-toggle');
    if (unansweredToggle) {
        unansweredToggle.addEventListener('change', function(e) {
            filterState.showUnansweredOnly = e.target.checked;
            applyFilters();
        });
    }

    var maturitySelect = document.getElementById('filter-maturity-select');
    if (maturitySelect) {
        maturitySelect.addEventListener('change', function(e) {
            filterState.selectedMaturityLevel = e.target.value;
            applyFilters();
        });
    }

    var sortSelect = document.getElementById('filter-sort-select');
    if (sortSelect) {
        sortSelect.addEventListener('change', function(e) {
            var sortBy = e.target.value || 'name';
            sortControls(sortBy);
            // After sorting, reapply filters to ensure visibility is preserved
            applyFilters();
        });
        // Initial sort based on current value
        sortControls(sortSelect.value || 'name');
    }

    // Initial update
    updateCompletionDisplay();
}

function populateMaturityFilter() {
    var selects = document.querySelectorAll('.answer-select');
    var maturitySet = new Set();
    
    selects.forEach(function(select) {
        select.querySelectorAll('option').forEach(function(option) {
            var text = option.text.trim();
            if (text && !text.includes('select an answer') && text !== '') {
                maturitySet.add(text);
            }
        });
    });
    
    filterState.maturityAnswers = Array.from(maturitySet).sort();
    
    // Populate the filter select
    var filterSelect = document.getElementById('filter-maturity-select');
    if (filterSelect && filterState.maturityAnswers.length > 0) {
        while (filterSelect.options.length > 1) {
            filterSelect.remove(1);
        }
        
        filterState.maturityAnswers.forEach(function(level) {
            var option = document.createElement('option');
            option.value = level;
            option.textContent = level;
            filterSelect.appendChild(option);
        });
    }
}

function updateAnsweredCount() {
    var allSelects = document.querySelectorAll('.answer-select');
    filterState.totalControls = 0;
    filterState.answeredControls = 0;
    
    allSelects.forEach(function(select) {
        var row = select.closest('tr');
        if (isRowNotApplicable(row)) {
            return;
        }
        filterState.totalControls++;
        if (select.value && select.value.trim() !== '') {
            filterState.answeredControls++;
        }
    });
    
    updateCompletionDisplay();
}

function updateCompletionDisplay() {
    var percentage = filterState.totalControls > 0
        ? Math.round((filterState.answeredControls / filterState.totalControls) * 100)
        : 0;

    var answeredEl = document.getElementById('answered-count');
    var totalEl = document.getElementById('total-count');
    var percentEl = document.getElementById('completion-percentage');
    var fillEl = document.getElementById('completion-bar-fill');

    if (answeredEl) answeredEl.textContent = filterState.answeredControls;
    if (totalEl) totalEl.textContent = filterState.totalControls;
    if (percentEl) percentEl.textContent = percentage + '%';
    if (fillEl) fillEl.style.width = percentage + '%';
}

// Sort controls within each domain table by the given attribute (name / reference / tag)
function sortControls(sortBy) {
    if (!sortBy) sortBy = 'name';
    var domains = document.querySelectorAll('.domain-outline.domain-collapsible');
    domains.forEach(function(domain) {
        var tbody = domain.querySelector('table.controls-table tbody');
        if (!tbody) return;
        var rows = Array.from(tbody.querySelectorAll('tr'));
        // Filter out any rows that don't represent a control (no data-control-id)
        rows = rows.filter(function(r) { return r.getAttribute('data-control-id') !== null; });

        rows.sort(function(a, b) {
            var va = (a.getAttribute('data-control-' + sortBy) || '').trim();
            var vb = (b.getAttribute('data-control-' + sortBy) || '').trim();
            // Empty values should be sorted last
            if (!va && !vb) return 0;
            if (!va) return 1;
            if (!vb) return -1;
            return va.localeCompare(vb, undefined, {numeric: true, sensitivity: 'base'});
        });

        // Re-append in sorted order
        rows.forEach(function(r) { tbody.appendChild(r); });
    });
}

function applyFilters() {
    var allRows = document.querySelectorAll('.controls-table tbody tr');
    var visibleCount = 0;
    var isFilterActive = filterState.showUnansweredOnly || filterState.selectedMaturityLevel;

    // Ensure domain completeness icons are up-to-date so we can rely on them
    checkDomainCompleteness();

    allRows.forEach(function(row) {
        var select = row.querySelector('.answer-select');
        var showRow = true;

        if (!select) return;

        var isNotApplicable = isRowNotApplicable(row);

        if (isNotApplicable) {
            row.style.display = filterState.showUnansweredOnly ? 'none' : '';
            if (!filterState.showUnansweredOnly) visibleCount++;
            return;
        }

        // Check if control is unanswered (filter for unanswered)
        if (filterState.showUnansweredOnly) {
            if (select.value && select.value.trim() !== '') {
                showRow = false;
            }
        }

        // Check maturity level filter
        if (showRow && filterState.selectedMaturityLevel) {
            if (select.value) {
                var selectedOption = select.querySelector('option[value="' + select.value + '"]');
                if (selectedOption && selectedOption.text.trim() !== filterState.selectedMaturityLevel) {
                    showRow = false;
                }
            } else {
                var matchingOption = false;
                var options = select.querySelectorAll('option');
                for (var i = 0; i < options.length; i++) {
                    if (options[i].text.trim() === filterState.selectedMaturityLevel) {
                        matchingOption = true;
                        break;
                    }
                }
                if (!matchingOption) {
                    showRow = false;
                }
            }
        }

        row.style.display = showRow ? '' : 'none';
        if (showRow) visibleCount++;
    });

    // Show/hide domains and empty state message
    var domains = document.querySelectorAll('.domain-outline.domain-collapsible');
    domains.forEach(function(domain) {
        var table = domain.querySelector('.controls-table');
        if (!table) return;

        // If a filter is active and the domain is fully answered we should hide the whole domain
        var checkmarkSpan = domain.querySelector('.domain-checkmark');
        var domainComplete = checkmarkSpan && checkmarkSpan.innerHTML && checkmarkSpan.innerHTML.trim() !== '';
        if (isFilterActive && domainComplete) {
            domain.style.display = 'none';
            // remove any empty-row messages if present
            var existingEmpty = table.querySelector('.no-results-message');
            if (existingEmpty) existingEmpty.parentNode.removeChild(existingEmpty);
            return;
        }

        var bodyRows = table.querySelectorAll('tbody tr');
        var visibleRows = [];
        bodyRows.forEach(function(row) {
            if (row.style.display !== 'none' && !row.classList.contains('no-results-message')) {
                visibleRows.push(row);
            }
        });

        if (visibleRows.length === 0) {
            // Hide domain when filter is active and no rows match
            if (isFilterActive) {
                domain.style.display = 'none';
            }
            // Show empty state message when no filters active
            if (!isFilterActive) {
                if (!table.querySelector('.no-results-message')) {
                    var emptyRow = document.createElement('tr');
                    emptyRow.className = 'no-results-message';
                    emptyRow.innerHTML = '<td colspan="2" style="text-align: center; padding: 20px; color: #999;">No controls match the current filters.</td>';
                    table.querySelector('tbody').appendChild(emptyRow);
                }
                domain.style.display = '';
            } else {
                var emptyRow = table.querySelector('.no-results-message');
                if (emptyRow) emptyRow.parentNode.removeChild(emptyRow);
            }
        } else {
            // Show domain and remove empty state
            domain.style.display = '';
            var emptyRow = table.querySelector('.no-results-message');
            if (emptyRow) emptyRow.parentNode.removeChild(emptyRow);
        }
    });
}

// Simple debounce helper for frequent updates
function debounce(func, wait) {
    var timeout;
    return function() {
        var context = this, args = arguments;
        clearTimeout(timeout);
        timeout = setTimeout(function() {
            func.apply(context, args);
        }, wait);
    };
}

var debouncedUpdateMaturityChart = debounce(function() {
    try {
        initializeMaturityRatingChart();
    } catch (e) {
        // ignore
    }
}, 300);

function generateGradientColorsForPie(n) {
    if (n === 0) return [];
    var start = { r: 220, g: 53, b: 69 };   // red
    var end   = { r: 40,  g: 167, b: 69 };  // green
    var cols = [];
    for (var i = 0; i < n; i++) {
        var t = n === 1 ? 0 : i / (n - 1);
        var r = Math.round(start.r + (end.r - start.r) * t);
        var g = Math.round(start.g + (end.g - start.g) * t);
        var b = Math.round(start.b + (end.b - start.b) * t);
        cols.push('rgb(' + r + ',' + g + ',' + b + ')');
    }
    return cols;
}

function updatePieChart() {
    if (typeof chartInstance === 'undefined' || !chartInstance) return;
    var selects = Array.from(document.querySelectorAll('.answer-select'));
    var counts = {};
    selects.forEach(function(sel) {
        if (isRowNotApplicable(sel.closest('tr'))) return;
        var selected = sel.options[sel.selectedIndex];
        if (!selected || !selected.value || selected.value === '') return;
        var label = (selected.textContent || selected.text || '').trim();
        if (!label || label.indexOf('select an answer') !== -1) return;
        counts[label] = (counts[label] || 0) + 1;
    });
    var data = Object.keys(counts).map(function(label) {
        return { label: label, count: counts[label] };
    }).sort(function(a, b) { return a.label.localeCompare(b.label); });
    if (data.length === 0) return;
    var total = data.reduce(function(sum, d) { return sum + d.count; }, 0);
    var labels = data.map(function(d) { return d.label; });
    var values = data.map(function(d) { return d.count; });
    var colors = generateGradientColorsForPie(data.length);
    chartInstance.data.labels = labels;
    chartInstance.data.datasets[0].data = values;
    chartInstance.data.datasets[0].backgroundColor = colors;
    chartInstance.update();
    var legendContainer = document.querySelector('.summary-legend');
    if (legendContainer) {
        var html = '';
        data.forEach(function(item, idx) {
            var percent = total > 0 ? Math.round(item.count / total * 100) : 0;
            html += '<div class="legend-item" style="border-left-color: ' + colors[idx] + ';">' +
                '<div class="legend-label"><strong>' + item.label + '</strong></div>' +
                '<div class="legend-values">' +
                '<span class="legend-count">' + item.count + ' answer(s)</span>' +
                '<span class="legend-percent">' + percent + '%</span>' +
                '</div></div>';
        });
        legendContainer.innerHTML = html;
    }
}

var debouncedUpdatePieChart = debounce(updatePieChart, 300);

document.addEventListener("DOMContentLoaded", function () {
    document.querySelectorAll('tr[data-control-id]').forEach(function(row) {
        captureBaseDisabledState(row);
        setRowNotApplicableState(row, isRowNotApplicable(row));
    });

    // Initialize filter bar first
    initializeFilterBar();

    // Initial domain completeness and chart rendering
    checkDomainCompleteness();
    // Try to initialize maturity chart after a small delay to ensure DOM is fully rendered
    setTimeout(function() {
        try {
            initializeMaturityRatingChart();
        } catch (e) {}
    }, 250);

    document.body.addEventListener("change", function (e) {
        if (e.target.classList.contains("answer-select")) {
            checkDomainCompleteness();
            updateAnsweredCount();
            // Update charts when answers change
            debouncedUpdateMaturityChart();
            debouncedUpdatePieChart();
        }
    });

    // Collapse assessment details by default on page load
    var adBody = document.querySelector('.assessment-details-body');
    if (adBody) adBody.style.display = 'none';
});
