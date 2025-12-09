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

// =================== Override Functions ===================
// Store collapse state before any action
function storeCollapsedState() {
    var collapsedDomains = [];
    var domainIndex = 0;
    document.querySelectorAll('.domain-collapsible').forEach(function(domain) {
        var controls = domain.querySelector('.domain-controls');
        if (controls) {
            // Store if this domain's controls are hidden
            collapsedDomains.push(controls.style.display === 'none');
        }
        domainIndex++;
    });
    sessionStorage.setItem('collapsedDomains', JSON.stringify(collapsedDomains));
}

// Restore collapse state after page reload
function restoreCollapsedState() {
    var collapsedDomainsStr = sessionStorage.getItem('collapsedDomains');
    if (!collapsedDomainsStr) return;
    
    var collapsedDomains = JSON.parse(collapsedDomainsStr);
    var domainIndex = 0;
    document.querySelectorAll('.domain-collapsible').forEach(function(domain) {
        if (domainIndex < collapsedDomains.length) {
            var controls = domain.querySelector('.domain-controls');
            var header = domain.querySelector('.domain-header');
            var chevron = header ? header.querySelector('.domain-chevron svg') : null;
            
            if (controls) {
                if (collapsedDomains[domainIndex]) {
                    // Collapsed state
                    controls.style.display = 'none';
                    if (chevron) {
                        chevron.style.transform = '';
                    }
                } else {
                    // Expanded state
                    controls.style.display = 'block';
                    if (chevron) {
                        chevron.style.transform = 'rotate(180deg)';
                    }
                }
            }
        }
        domainIndex++;
    });
    
    // Clear the session storage after restoring
    sessionStorage.removeItem('collapsedDomains');
}

// Override function - applies first available answer silently
function openOverrideModal(button) {
    var controlId = button.getAttribute('data-control-id');
    var assessmentId = window.assessmentId || 0;
    
    // Get the dropdown for this control to find available answers
    var $dropdown = $('select[data-control-id="' + controlId + '"]');
    
    // Build a list of available answers from the dropdown
    var options = [];
    $dropdown.find('option').each(function() {
        var val = $(this).val();
        var text = $(this).text();
        if (val && text !== '-- select an answer --') {
            options.push({ id: val, text: text });
        }
    });
    
    if (options.length === 0) {
        return;
    }
    
    // Use the first available answer as the override
    var answerId = options[0].id;
    
    // Store the collapsed state before reload
    storeCollapsedState();
    
    // Save the override
    $.ajax({
        url: '/assessment/' + assessmentId + '/answer-override',
        type: 'POST',
        data: { controlId: controlId, answerId: answerId },
        success: function() {
            location.reload();
        },
        error: function() {
            // Silently fail, clear session storage
            sessionStorage.removeItem('collapsedDomains');
        }
    });
}

// Remove override function - reverts silently
function removeOverride(button) {
    var controlId = button.getAttribute('data-control-id');
    var assessmentId = button.getAttribute('data-assessment-id');
    
    // Store the collapsed state before reload
    storeCollapsedState();
    
    // Remove override silently without confirmation
    $.ajax({
        url: '/assessment/' + assessmentId + '/control/' + controlId + '/remove-override',
        type: 'POST',
        success: function() {
            location.reload();
        },
        error: function() {
            // Silently fail, clear session storage
            sessionStorage.removeItem('collapsedDomains');
        }
    });
}

// =================== Modal Logic and Dynamic Content Loading ===================
$(document).ready(function () {
    // Org Unit modal logic
    $('#choose-orgunit-btn').click(function () {
        const $select = $('#orgunit-select');
        $select.prop('disabled', true);
        $select.html('<option>Loading...</option>');
        var currentOrgUnitId = window.currentOrgUnitId || '';
        $.getJSON('/assessmentdetails/orgunits', function (units) {
            $select.empty();
            $select.append($('<option>').val('').text('-- Please choose an organization unit --'));
            units.forEach(function (unit) {
                let $opt = $('<option>').val(unit.id).text(unit.name);
                if (currentOrgUnitId && String(unit.id) === String(currentOrgUnitId)) {
                    $opt.prop('selected', true);
                }
                $select.append($opt);
            });
            $select.prop('disabled', false);
        });
        $('#orgunit-modal-bg').css('display', 'flex');
    });

    // User modal logic
    $('#choose-users-btn').click(function () {
        var $modal = $('#users-modal-bg');
        var $error = $('#users-modal-error');
        $error.hide();
        $modal.css('display', 'flex');

        // Load users via AJAX
        var $select = $('#users-multiselect');
        $select.prop('disabled', true);
        $select.html('<option>Loading...</option>');

        // Get assigned user IDs from Thymeleaf/JS variable
        var assignedUserIds = window.assessmentAssignedUserIds || [];
        $.getJSON('/users/api', function (users) {
            $select.empty();
            if (!$.isArray(users) || users.length === 0) {
                $select.append($('<option>').val('').text('(No users found)'));
            } else {
                users.forEach(function (user) {
                    var $opt = $('<option>').val(user.id).text(user.name + ' (' + user.email + ')');
                    if (assignedUserIds.includes(user.id) || assignedUserIds.includes(user.id.toString())) {
                        $opt.prop('selected', true);
                    }
                    $select.append($opt);
                });
            }
            $select.prop('disabled', false);
        }).fail(function () {
            $select.html('<option value="">(Could not load users)</option>');
        });
    });

    $('#users-modal-cancel-btn').click(function () {
        $('#users-modal-bg').hide();
    });

    $('#users-modal-bg').on('mousedown', function (event) {
        if (event.target === this) { $('#users-modal-bg').hide(); }
    });

    // AJAX Save users selection
    $('#users-modal-form').submit(function (event) {
        event.preventDefault();
        var $select = $('#users-multiselect');
        var selected = $select.val() || [];
        var assessmentId = window.assessmentId || 0;
        $('#users-modal-save-btn').prop('disabled', true);
        $('#users-modal-error').hide();
        $.ajax({
            url: '/assessment/' + assessmentId + '/users',
            type: 'PUT',
            contentType: 'application/json',
            data: JSON.stringify(selected),
            success: function (updatedUsers) {
                // Update users in the table
                if (updatedUsers && Array.isArray(updatedUsers)) {
                    var html = '';
                    if (updatedUsers.length === 0) {
                        html = 'None';
                    } else {
                        for (let i = 0; i < updatedUsers.length; i++) {
                            const u = updatedUsers[i];
                            html += $('<span/>').text(u.name + ' (' + u.email + ')').prop('outerHTML');
                            if (i !== updatedUsers.length - 1) html += ', ';
                        }
                    }
                    $('#assessment-users-cell').html(html);
                }
                $('#users-modal-bg').hide();
                $('#users-modal-save-btn').prop('disabled', false);
                $('#users-modal-error').hide();
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
        
        $.getJSON('/orgservices/all', function (allServices) {
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
        $("#create-url-feedback").text('Creating...');
        $.ajax({
            url: '/assessment/' + assessmentId + '/create-url',
            type: 'POST',
            success: function (data) {
                $("#create-url-feedback").html('<span style="color: green;">Created!</span>');
                setTimeout(function () {
                    $("#create-url-feedback").empty();
                    $("#create-url-btn").prop('disabled', false);
                }, 1800);
                if (data && data.directUrl) {
                    $("#direct-url-link").attr('href', data.directUrl);
                    $("#direct-url-link").text(window.location.origin + data.directUrl);
                    $("#direct-url-row").show();
                    var $urlTd = $("th:contains('Assessment URL')").next('td');
                    if ($urlTd.length > 0) {
                        var anchor = $urlTd.find('a');
                        if (anchor.length > 0) {
                            anchor.attr('href', data.directUrl);
                            anchor.text(window.location.origin + data.directUrl);
                            anchor.parent().show();
                            anchor.parent().siblings('span').hide();
                        } else {
                            $urlTd.html('<span><a href="' + data.directUrl + '" target="_blank">' + window.location.origin + data.directUrl + '</a></span>');
                        }
                    }
                }
            },
            error: function (err) {
                $("#create-url-feedback").html('<span style="color: red;">Error creating URL</span>');
                $("#create-url-btn").prop('disabled', false);
            }
        });
    });

    // Save answer on dropdown change
    $('.answer-select').on('change', function () {
        var select = $(this);
        var controlId = select.data('control-id');
        var answerId = select.val();
        var feedbackIcon = select.closest('span').find('.answer-feedback');
        select.css('background', '');
        feedbackIcon.empty();
        if (!answerId) {
            feedbackIcon.empty();
            return;
        }
        let assessmentId = window.assessmentId || 0;
        if (assessmentId && controlId) {
            $.ajax({
                url: '/assessment/' + assessmentId + '/answer',
                type: 'POST',
                data: { controlId: controlId, answerId: answerId },
                success: function () {
                    select.css('background', '#d8ffd8');
                    feedbackIcon.html('<span class="answer-success" title="Saved"><svg viewBox="0 0 18 18"><circle cx="9" cy="9" r="8" fill="#d8ffd8" stroke="#33aa33" stroke-width="2"/><path d="M5 9l3 3 5-5" fill="none" stroke="#33aa33" stroke-width="2"/></svg></span>');
                    setTimeout(function () {
                        feedbackIcon.empty();
                        select.css('background', '');
                    }, 1500);
                },
                error: function () {
                    select.css('background', '#ffd8d8');
                    feedbackIcon.html('<span class="answer-error" title="Error saving"><svg viewBox="0 0 18 18"><circle cx="9" cy="9" r="8" fill="#ffd8d8" stroke="#cc2222" stroke-width="2"/><path d="M6 6l6 6M12 6l-6 6" fill="none" stroke="#cc2222" stroke-width="2"/></svg></span>');
                }
            });
        }
    });

    // Word Report Progress Handler with real-time polling
    $('#word-report-btn').click(function (e) {
        e.preventDefault();
        showWordReportProgress();
        setTimeout(function () {
            $('#word-report-form').submit();
        }, 100);
    });
});

// =================== Answering Guide Functions ===================
var currentAnsweringGuideState = {
    controlId: null,
    securityCatalogId: null,
    questions: [],
    answers: {},
    proposedAnswer: null
};

function openAnsweringGuideModal(controlId, controlName, controlDetail, securityCatalogId) {
    currentAnsweringGuideState.controlId = controlId;
    currentAnsweringGuideState.securityCatalogId = securityCatalogId;
    currentAnsweringGuideState.questions = [];
    currentAnsweringGuideState.answers = {};
    currentAnsweringGuideState.proposedAnswer = null;

    $('#answering-guide-control-name').html('<h4>' + $('<span/>').text(controlName).html() + '</h4><p>' + $('<span/>').text(controlDetail).html() + '</p>');
    $('#answering-guide-questions').empty();
    $('#answering-guide-proposed-answer').hide();
    $('#answering-guide-loading').show();
    $('#answering-guide-modal-bg').css('display', 'flex');

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
            if (response && response.questions && Array.isArray(response.questions)) {
                currentAnsweringGuideState.questions = response.questions;
                displayAnsweringGuideQuestions(response.questions);
            } else {
                $('#answering-guide-questions').html('<p style="color: red;">Error: Could not load questions.</p>');
            }
        },
        error: function(xhr) {
            $('#answering-guide-loading').hide();
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
        html += '<label class="question-label">' + (index + 1) + '. ' + $('<span/>').text(q).html() + '</label>';
        html += '<div class="question-answer-options">';
        html += '<label class="option-label"><input type="radio" name="question_' + index + '" value="Yes" class="question-answer-input" data-question-index="' + index + '"> Yes</label>';
        html += '<label class="option-label"><input type="radio" name="question_' + index + '" value="No" class="question-answer-input" data-question-index="' + index + '"> No</label>';
        html += '</div>';
        html += '</div>';
    });
    html += '<button type="button" class="guide-submit-answers-btn" onclick="submitAnsweringGuideAnswers()">Submit Answers</button>';
    html += '</div>';
    $('#answering-guide-questions').html(html);
}

function submitAnsweringGuideAnswers() {
    var answers = [];
    var allAnswered = true;
    for (var i = 0; i < currentAnsweringGuideState.questions.length; i++) {
        var selectedValue = $('input[name="question_' + i + '"]:checked').val();
        if (!selectedValue) {
            allAnswered = false;
            break;
        }
        answers.push(selectedValue);
    }
    
    if (!allAnswered) {
        alert('Please answer all questions before submitting.');
        return;
    }
    
    currentAnsweringGuideState.answers = answers;

    // Send answers to backend to get proposed answer
    $.ajax({
        url: '/assessment/generate-answer-from-guide',
        type: 'POST',
        contentType: 'application/json',
        data: JSON.stringify({
            controlId: currentAnsweringGuideState.controlId,
            securityCatalogId: currentAnsweringGuideState.securityCatalogId,
            questions: currentAnsweringGuideState.questions,
            answers: answers
        }),
        success: function(response) {
            if (response && response.proposedAnswer) {
                currentAnsweringGuideState.proposedAnswer = response.proposedAnswer;
                $('#guide-proposed-answer-text').text(response.proposedAnswer);
                $('#answering-guide-proposed-answer').show();
            } else {
                alert('Error generating answer. Please try again.');
            }
        },
        error: function(xhr) {
            var errorMsg = 'Error generating answer. Please try again.';
            if (xhr.responseJSON && xhr.responseJSON.message) {
                errorMsg = xhr.responseJSON.message;
            }
            alert(errorMsg);
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
            selectElement.val(matchedOption.val()).change();
            closeAnsweringGuideModal();
        } else {
            alert('Proposed answer does not match available options. Please select manually.');
        }
    } else {
        alert('Could not find answer dropdown for this control.');
    }
}

function discardProposedAnswer() {
    closeAnsweringGuideModal();
}

function closeAnsweringGuideModal() {
    $('#answering-guide-modal-bg').css('display', 'none');
    currentAnsweringGuideState = {
        controlId: null,
        securityCatalogId: null,
        questions: [],
        answers: {},
        proposedAnswer: null
    };
}

function closeOrgUnitModal() {
    $('#orgunit-modal-bg').hide();
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
    }, 300);
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
        if (selects.length === 0) allAnswered = false;
        selects.forEach(select => {
            if (!select.value) allAnswered = false;
        });
        checkmarkSpan.innerHTML = allAnswered ? CHECKMARK_SVG : "";
    });
}

document.addEventListener("DOMContentLoaded", function () {
    checkDomainCompleteness();
    document.body.addEventListener("change", function (e) {
        if (e.target.classList.contains("answer-select")) {
            checkDomainCompleteness();
        }
    });
    
    // Collapse assessment details by default on page load
    var adBody = document.querySelector('.assessment-details-body');
    if (adBody) adBody.style.display = 'none';
    
    // Restore collapsed domains state if available
    restoreCollapsedState();
});
