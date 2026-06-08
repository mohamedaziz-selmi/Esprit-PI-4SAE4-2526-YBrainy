document.addEventListener("DOMContentLoaded", function () {
  var Calendar = FullCalendar.Calendar;
  var calendarEl = document.getElementById("calendar");
  var form = document.getElementById("eventForm");
  var saveBtn = document.getElementById("saveEventBtn");
  var modalEl = document.getElementById("exampleModal");
  var modalTitle = document.getElementById("exampleModalLabel");
  var openModalBtn = document.querySelector('button[data-bs-target="#exampleModal"]');
  var waitlistModalEl = document.getElementById("ybWaitlistModal");
  var waitlistModalTitle = document.getElementById("ybWaitlistModalLabel");
  var waitlistModalMeta = document.getElementById("ybWaitlistModalMeta");
  var waitlistModalBody = document.getElementById("ybWaitlistModalBody");
  var sideRow = document.getElementById("ybEventsSideRow");
  var overviewMount = document.getElementById("ybOverviewMount");
  var calendarCard = document.getElementById("ybCalendarCard");
  var calendarExpandBtn = document.getElementById("ybCalendarExpandBtn");
  var compactCalendarMonth = document.getElementById("ybCompactCalendarMonth");
  var calendarTodaySummary = document.getElementById("ybCalendarTodaySummary");
  var calendarViewAllBtn = document.getElementById("ybCalendarViewAll");
  var monthFilter = document.getElementById("calendarMonthFilter");
  var yearFilter = document.getElementById("calendarYearFilter");
  var eventSearchTop = document.getElementById("eventSearchTop");
  var startDateInput = document.getElementById("eventDateDebut");
  var endDateInput = document.getElementById("eventDateFin");
  var eventNameInput = document.getElementById("eventName");
  var eventDescriptionInput = document.getElementById("eventDescription");
  var eventDescriptionHint = document.getElementById("eventDescriptionHint");
  var eventDescriptionHintCopy = eventDescriptionHint ? eventDescriptionHint.querySelector(".yb-ai-status-copy") : null;
  var eventImageUrlInput = document.getElementById("eventImageUrl");
  var eventImageFileInput = document.getElementById("eventImageFile");
  var eventImageHint = document.getElementById("eventImageHint");
  var eventImageHintCopy = eventImageHint ? eventImageHint.querySelector(".yb-ai-status-copy") : null;
  var eventImagePreviewWrap = document.getElementById("ybEventImagePreview");
  var eventImagePreview = document.getElementById("eventImagePreview");
  var eventLocationInput = document.getElementById("eventLocation");
  var eventLocationSearchBtn = document.getElementById("eventLocationSearchBtn");
  var eventLocationResults = document.getElementById("eventLocationResults");
  var eventLocationMapEl = document.getElementById("eventLocationMap");
  var eventLocationStatus = document.getElementById("eventLocationStatus");
  var msgOverlay = document.getElementById("ybMsgOverlay");
  var msgText = document.getElementById("ybMsgText");
  var msgCloseBtn = document.getElementById("ybMsgCloseBtn");
  var eventSortFilter = document.getElementById("eventSortFilter");
  var eventStatusFilter = document.getElementById("eventStatusFilter");
  var eventDateFilter = document.getElementById("eventDateFilter");
  var eventFilterInfo = document.getElementById("eventFilterInfo");
  var eventTrendCurrentEl = document.getElementById("ybEventTrendCurrent");
  var eventTrendPreviousEl = document.getElementById("ybEventTrendPrevious");
  var overviewRangeTabs = document.getElementById("ybOverviewRangeTabs");
  var API_BASE = window.EVENT_API_BASE || "http://localhost:8081/Event";
  var INSCRIPTION_API_BASE = (window.INSCRIPTION_API_BASE || API_BASE.replace(/\/Event$/, "/Inscription"));
  var DESCRIPTION_API_PATH = "/generate-description";
  var IMAGE_API_PATH = "/generate-image";
  var IMAGE_UPLOAD_API_PATH = "/upload-image";
  var eventsCache = [];
  var pendingInscriptionsCache = [];
  var adminNotificationsCache = [];
  var notificationLoadIssues = [];
  var listContainer = null;
  var overviewStats = null;
  var listScrollArea = null;
  var paginationBar = null;
  var currentPage = 1;
  var bellNotificationTimeline = document.querySelector("#DZ_W_Notification1 .timeline");
  var bellNotificationBody = document.getElementById("DZ_W_Notification1");
  var isSaving = false;
  var lastGeneratedDescription = "";
  var lastGeneratedImageUrl = "";
  var descriptionGenerationTimer = null;
  var imageGenerationTimer = null;
  var activeDescriptionRequestId = 0;
  var activeImageRequestId = 0;
  var imageSourceMode = "idle";
  var locationSearchTimer = null;
  var locationMap = null;
  var locationMarker = null;
  var locationPopup = null;
  var modal = (window.bootstrap && bootstrap.Modal) ? bootstrap.Modal.getOrCreateInstance(modalEl) : null;
  var waitlistModal = (window.bootstrap && waitlistModalEl) ? bootstrap.Modal.getOrCreateInstance(waitlistModalEl) : null;
  var eventTrendChart = null;
  var eventOverviewChart = null;
  var eventOverviewRange = "week";
  var latestAnalyticsRequestId = 0;

  if (!calendarEl || !form || !modalEl || !sideRow || !overviewMount) {
    return;
  }

  var calendar = new Calendar(calendarEl, {
    headerToolbar: {
      left: "prev,next today",
      center: "title",
      right: "dayGridMonth,timeGridWeek,timeGridDay"
    },
    initialDate: new Date(),
    navLinks: true,
    editable: false,
    droppable: false,
    dayMaxEvents: true,
    events: [],
    datesSet: function () {
      syncMonthYearFilterWithCalendar();
      updateCompactCalendarMeta();
    },
    eventClick: function (info) {
      var idEvent = Number(info.event.id);
      var eventData = eventsCache.find(function (ev) {
        return Number(ev.idEvent) === idEvent;
      });
      if (eventData) {
        fillFormForEdit(eventData);
      }
    }
  });

  calendar.render();
  prepareRightPanel();
  initCalendarExpand();
  applyDateInputBounds();
  initMonthYearFilters();
  initEventFilters();
  initLocationPicker();
  refreshTopBarSelects();
  initAnalyticsCards();

  if (openModalBtn) {
    openModalBtn.addEventListener("click", function () {
      resetFormForCreate();
    });
  }

  if (msgCloseBtn) {
    msgCloseBtn.addEventListener("click", function () {
      hideStyledMessage();
    });
  }
  if (msgOverlay) {
    msgOverlay.addEventListener("click", function (event) {
      if (event.target === msgOverlay) {
        hideStyledMessage();
      }
    });
  }

  if (startDateInput) {
    startDateInput.addEventListener("change", function () {
      enforceDateNotBeforeToday(startDateInput, "Start Date");
      enforceEndAfterStart();
      applyDateInputBounds();
    });
  }
  if (endDateInput) {
    endDateInput.addEventListener("change", function () {
      enforceDateNotBeforeToday(endDateInput, "End Date");
      enforceEndAfterStart();
      applyDateInputBounds();
    });
  }
  if (eventNameInput) {
    eventNameInput.addEventListener("input", function () {
      syncDescriptionFromEventNameDebounced();
      syncImageFromEventContentDebounced();
    });
  }
  if (eventDescriptionInput) {
    eventDescriptionInput.addEventListener("input", function () {
      if (eventDescriptionInput.value !== lastGeneratedDescription) {
        eventDescriptionInput.dataset.manualEdit = "true";
      }
      syncImageFromEventContentDebounced();
    });
  }
  if (eventImageFileInput) {
    eventImageFileInput.addEventListener("change", function () {
      void uploadSelectedEventImage();
    });
  }
  if (eventLocationInput) {
    eventLocationInput.addEventListener("keydown", function (event) {
      if (event.key === "Enter") {
        event.preventDefault();
        performLocationSearch();
      }
    });
    eventLocationInput.addEventListener("input", function () {
      if (locationSearchTimer) {
        clearTimeout(locationSearchTimer);
      }
      var query = eventLocationInput.value.trim();
      if (query.length < 3) {
        renderLocationResults([]);
        updateLocationStatus("Search a venue name or click on the map.");
        return;
      }
      locationSearchTimer = setTimeout(function () {
        performLocationSearch();
      }, 550);
    });
  }
  if (eventLocationSearchBtn) {
    eventLocationSearchBtn.addEventListener("click", function () {
      performLocationSearch();
    });
  }
  if (eventImagePreview) {
    eventImagePreview.addEventListener("error", function () {
      if (eventImagePreviewWrap) {
        eventImagePreviewWrap.classList.remove("has-image");
      }
      updateImageHint("The image URL was generated, but the preview provider did not return an image.", "warning");
    });
    eventImagePreview.addEventListener("load", function () {
      if (eventImagePreviewWrap && eventImagePreview.src) {
        eventImagePreviewWrap.classList.add("has-image");
      }
    });
  }
  var eventTypeField = document.getElementById("eventType");
  if (eventTypeField) {
    eventTypeField.addEventListener("change", function () {
      syncImageFromEventContentDebounced();
    });
  }

  if (saveBtn) {
    saveBtn.addEventListener("click", async function (event) {
      event.preventDefault();
      await handleSave();
    });
  } else {
    form.addEventListener("submit", async function (event) {
      event.preventDefault();
      await handleSave();
    });
  }

  document.addEventListener("click", async function (event) {
    var target = event.target.closest("#saveEventBtn");
    if (!target) return;
    event.preventDefault();
    await handleSave();
  });
  window.__ybrainySaveEvent = handleSave;

  async function handleSave() {
    if (isSaving) {
      return;
    }

    var validationError = validateFormInputs();
    if (validationError) {
      showStyledMessage(validationError);
      return;
    }

    var idValue = document.getElementById("eventId").value;
    var isUpdate = Boolean(idValue);
    var payload = buildPayload();

    if (isUpdate) {
      payload.idEvent = Number(idValue);
    }

    isSaving = true;
    if (saveBtn) {
      saveBtn.disabled = true;
    }

    try {
      var requestPath = isUpdate ? "/update" : "/add";
      var requestMethod = isUpdate ? "PUT" : "POST";
      if (!isUpdate) {
        delete payload.idEvent;
      }
      await requestApi(requestPath, {
        method: requestMethod,
        body: JSON.stringify(payload)
      });
      if (modal) {
        modal.hide();
      }
      resetFormForCreate();
      await loadEvents();
    } catch (error) {
      showStyledMessage("Save failed: " + error.message);
      console.error(error);
    } finally {
      isSaving = false;
      if (saveBtn) {
        saveBtn.disabled = false;
      }
    }
  }

  document.addEventListener("click", async function (event) {
    var confirmInscriptionBtn = event.target.closest("[data-action='confirm-inscription']");
    var refuseInscriptionBtn = event.target.closest("[data-action='refuse-inscription']");
    var editBtn = event.target.closest("[data-action='edit-event']");
    var archiveBtn = event.target.closest("[data-action='archive-event']");

    if (confirmInscriptionBtn || refuseInscriptionBtn) {
      var inscriptionId = Number((confirmInscriptionBtn || refuseInscriptionBtn).getAttribute("data-id"));
      if (!Number.isFinite(inscriptionId) || inscriptionId <= 0) {
        showStyledMessage("Invalid inscription id.");
        return;
      }

      var targetStatus = confirmInscriptionBtn ? "CONFIRMEE" : "ANNULEE";
      try {
        await requestInscriptionApi("/" + inscriptionId + "/status/" + targetStatus, { method: "PUT" });
        showStyledMessage(targetStatus === "CONFIRMEE" ? "Inscription confirmed." : "Inscription refused.");
        await loadEvents();
      } catch (error) {
        showStyledMessage("Inscription update failed: " + error.message);
        console.error(error);
      }
      return;
    }
  });

  sideRow.addEventListener("click", async function (event) {
    var clickTarget = event.target instanceof Element ? event.target : null;
    var waitlistBtn = clickTarget ? clickTarget.closest("button[data-action='view-waitlist']") : null;
    var editBtn = clickTarget ? clickTarget.closest("button[data-action='edit-event']") : null;
    var archiveBtn = clickTarget ? clickTarget.closest("button[data-action='archive-event']") : null;

    if (waitlistBtn) {
      var waitlistEventId = Number(waitlistBtn.getAttribute("data-id") || waitlistBtn.dataset.id || "");
      if (!Number.isFinite(waitlistEventId) || waitlistEventId <= 0) {
        showStyledMessage("Waitlist loading failed: invalid event id.");
        return;
      }
      var waitlistEvent = eventsCache.find(function (ev) {
        return Number(ev.idEvent) === waitlistEventId;
      });
      await openWaitlistModal(waitlistEventId, waitlistEvent && waitlistEvent.name ? waitlistEvent.name : "Event");
      return;
    }

    if (editBtn) {
      if (editBtn.hasAttribute("disabled")) {
        showStyledMessage("This event is terminated and can no longer be modified.");
        return;
      }
      var editId = Number(editBtn.getAttribute("data-id") || editBtn.dataset.id || "");
      var targetEvent = eventsCache.find(function (ev) {
        return Number(ev.idEvent) === editId;
      });
      if (targetEvent) {
        fillFormForEdit(targetEvent);
      }
      return;
    }

    if (archiveBtn) {
      if (archiveBtn.hasAttribute("disabled")) {
        showStyledMessage("This event is already terminated.");
        return;
      }
      var archiveId = Number(archiveBtn.getAttribute("data-id") || archiveBtn.dataset.id || "");
      var eventToArchive = eventsCache.find(function (ev) {
        return Number(ev.idEvent) === archiveId;
      });
      if (!eventToArchive) {
        showStyledMessage("Archive failed: event not found.");
        return;
      }
      if (!confirm("Archive this event?")) {
        return;
      }
      try {
        var archivePayload = {
          idEvent: Number(eventToArchive.idEvent),
          name: eventToArchive.name || "",
          description: eventToArchive.description || "",
          imageUrl: eventToArchive.imageUrl || "",
          location: eventToArchive.location || "",
          capacite: Number(eventToArchive.capacite || 0),
          dateDebut: eventToArchive.dateDebut,
          dateFin: eventToArchive.dateFin,
          type: eventToArchive.type || "ATELIER",
          statut: "TERMINE"
        };
        await requestApi("/update", {
          method: "PUT",
          body: JSON.stringify(archivePayload)
        });
        await loadEvents();
      } catch (error) {
        showStyledMessage("Archive failed: " + error.message);
        console.error(error);
      }
    }
  });

  loadEvents();

  async function loadEvents() {
    try {
      var response = await Promise.allSettled([
        requestApi("/all"),
        requestInscriptionApi("/pending"),
        requestInscriptionApi("/admin-notifications")
      ]);
      notificationLoadIssues = [];

      eventsCache = response[0] && response[0].status === "fulfilled" && Array.isArray(response[0].value)
        ? response[0].value
        : [];
      pendingInscriptionsCache = response[1] && response[1].status === "fulfilled" && Array.isArray(response[1].value)
        ? response[1].value
        : [];
      adminNotificationsCache = response[2] && response[2].status === "fulfilled" && Array.isArray(response[2].value)
        ? response[2].value
        : [];

      if (!Array.isArray(eventsCache)) {
        eventsCache = [];
      }

      if (response[1] && response[1].status === "rejected") {
        notificationLoadIssues.push("Pending inscription requests could not be loaded.");
        console.warn("Pending inscriptions could not be loaded.", response[1].reason);
      }

      if (response[2] && response[2].status === "rejected") {
        notificationLoadIssues.push("Admin inscription updates could not be loaded.");
        console.warn("Admin notifications could not be loaded.", response[2].reason);
      }

      renderPendingInscriptionsInBell();
      renderAnalyticsCharts();
      applyEventFiltersAndRender();
    } catch (error) {
      eventsCache = [];
      pendingInscriptionsCache = [];
      adminNotificationsCache = [];
      notificationLoadIssues = ["Event and inscription data could not be loaded."];
      refreshCalendar([]);
      renderPendingInscriptionsInBell();
      renderAnalyticsCharts();
      renderEventCards([]);
      showStyledMessage("Failed to load events: " + (error && error.message ? error.message : "unknown error"));
      console.error(error);
    }
  }

  function refreshCalendar(events) {
    calendar.removeAllEvents();
    events.forEach(function (ev) {
      calendar.addEvent({
        id: String(ev.idEvent),
        title: ev.name || "Event",
        start: ev.dateDebut,
        end: ev.dateFin
      });
    });
    updateCompactCalendarMeta(events);
  }

  function prepareRightPanel() {
    Array.from(sideRow.children).forEach(function (child) {
      if (child.classList.contains("col-sm-6")) {
        child.remove();
        return;
      }
      var viewMore = child.querySelector(".btn-rounded.btn-block");
      if (viewMore) {
        child.remove();
      }
    });

    listContainer = document.createElement("div");
    listContainer.className = "col-xl-12";
    listContainer.id = "eventListContainer";
    listContainer.style.padding = "0";
    listContainer.style.margin = "0";

    var overviewShell = document.createElement("section");
    overviewShell.className = "yb-overview-shell";
    overviewShell.innerHTML =
      '<div '
      // class="yb-overview-header">' +
      // '<div>' +
      // '<span class="yb-overview-eyebrow">Event Overview</span>' +
      // '<p>Track key event signals, review capacity, and move through upcoming sessions with clarity.</p>' +
      // '</div>' +
      // '<button type="button" class="yb-overview-link" data-scroll-calendar="true">View Schedule</button>' +
      '</div>';

    overviewStats = document.createElement("div");
    overviewStats.id = "ybOverviewStats";
    overviewStats.className = "yb-overview-stats";

    listScrollArea = document.createElement("div");
    listScrollArea.id = "eventListScrollArea";
    listScrollArea.className = "yb-event-cards-grid";

    paginationBar = document.createElement("div");
    paginationBar.id = "eventListPagination";
    paginationBar.className = "yb-pagination-bar";

    overviewShell.appendChild(overviewStats);
    overviewMount.innerHTML = "";
    overviewMount.appendChild(overviewShell);
    listContainer.appendChild(listScrollArea);
    listContainer.appendChild(paginationBar);
    sideRow.appendChild(listContainer);
  }

  function initCalendarExpand() {
    if (!calendarCard || !calendarExpandBtn) return;

    function toggleCalendarExpanded(forceValue) {
      var willExpand = typeof forceValue === "boolean" ? forceValue : !calendarCard.classList.contains("is-expanded");
      calendarCard.classList.toggle("is-expanded", willExpand);
      document.body.classList.toggle("yb-calendar-expanded", willExpand);
      calendarExpandBtn.setAttribute("aria-expanded", willExpand ? "true" : "false");
      calendarExpandBtn.setAttribute("aria-label", willExpand ? "Reduce calendar" : "Expand calendar");
      calendarExpandBtn.setAttribute("title", willExpand ? "Reduce calendar" : "Expand calendar");
      setTimeout(function () {
        calendar.changeView("dayGridMonth");
        calendar.updateSize();
      }, 220);
    }

    calendarExpandBtn.addEventListener("click", function () {
      toggleCalendarExpanded();
    });

    if (calendarViewAllBtn) {
      calendarViewAllBtn.addEventListener("click", function () {
        toggleCalendarExpanded(true);
      });
    }
  }

  function renderPendingInscriptionsInBell() {
    if (!bellNotificationTimeline) return;
    var pendingItems = Array.isArray(pendingInscriptionsCache) ? pendingInscriptionsCache : [];
    var notificationItems = Array.isArray(adminNotificationsCache) ? adminNotificationsCache : [];
    var issueItems = Array.isArray(notificationLoadIssues) ? notificationLoadIssues : [];

    if (!pendingItems.length && !notificationItems.length && !issueItems.length) {
      bellNotificationTimeline.innerHTML =
        '<li><div class="timeline-panel"><div class="media me-2 media-info">i</div><div class="media-body"><h6 class="mb-1">No notifications</h6><small class="d-block">Waiting for new student requests or waitlist promotions.</small></div></div></li>';
      if (bellNotificationBody) {
        bellNotificationBody.style.height = "220px";
      }
      return;
    }

    var requestItems = pendingItems
      .map(function (ins) {
        var dateTxt = ins.dateInscription ? formatDate(ins.dateInscription) + " - " + formatTime(ins.dateInscription) : "";
        var rawStatus = String(ins.statut || "").toUpperCase();
        var isWaitlist = rawStatus === "LISTE_ATTENTE";
        var statusBadge = isWaitlist
          ? '<span style="display:inline-block;padding:4px 9px;border-radius:999px;background:rgba(214,108,63,0.14);color:#b45c2f;font-size:10px;font-weight:800;letter-spacing:.06em;text-transform:uppercase;margin-bottom:6px;">Waitlist</span>'
          : '<span style="display:inline-block;padding:4px 9px;border-radius:999px;background:rgba(77,86,216,0.12);color:#4d56d8;font-size:10px;font-weight:800;letter-spacing:.06em;text-transform:uppercase;margin-bottom:6px;">Pending</span>';
        return (
          "<li>" +
          '<div class="timeline-panel">' +
          '<div class="media me-2 ' + (isWaitlist ? 'media-danger' : 'media-warning') + '"><i class="fa ' + (isWaitlist ? 'fa-hourglass-half' : 'fa-bell') + '"></i></div>' +
          '<div class="media-body">' +
          statusBadge +
          '<h6 class="mb-1">' + (isWaitlist ? 'Student joined the waiting list' : 'New inscription request') + '</h6>' +
          '<small class="d-block">Student ID: ' + escapeHtml(ins.idStudent) + ' | Event: ' + escapeHtml(ins.eventName || ("#" + ins.idEvent)) + "</small>" +
          (dateTxt ? '<small class="d-block">' + escapeHtml(dateTxt) + "</small>" : "") +
          '<div style="display:flex;gap:8px;margin-top:8px;">' +
          '<button type="button" class="btn btn-success btn-xs" data-action="confirm-inscription" data-id="' + Number(ins.idInscription) + '"' + (isWaitlist ? ' disabled style="opacity:.5;cursor:not-allowed;"' : '') + '>Confirm</button>' +
          '<button type="button" class="btn btn-danger light btn-xs" data-action="refuse-inscription" data-id="' + Number(ins.idInscription) + '">Refuse</button>' +
          "</div>" +
          "</div>" +
          "</div>" +
          "</li>"
        );
      })
      .join("");

    var adminItems = notificationItems
      .map(function (notification) {
        var notificationType = String(notification.type || "").toUpperCase();
        var createdTxt = notification.createdAt ? formatDate(notification.createdAt) + " - " + formatTime(notification.createdAt) : "";
        var toneClass = notificationType === "INSCRIPTION_REFUSED"
          ? "media-danger"
          : notificationType === "INSCRIPTION_CONFIRMED"
            ? "media-success"
            : "media-primary";
        var iconClass = notificationType === "INSCRIPTION_REFUSED"
          ? "fa-times"
          : notificationType === "INSCRIPTION_CONFIRMED"
            ? "fa-check"
            : notificationType === "WAITLIST_PROMOTION"
              ? "fa-random"
              : "fa-bell";
        return (
          "<li>" +
          '<div class="timeline-panel">' +
          '<div class="media me-2 ' + toneClass + '"><i class="fa ' + iconClass + '"></i></div>' +
          '<div class="media-body">' +
          '<h6 class="mb-1">' + escapeHtml(notification.title || getAdminNotificationTitle(notification)) + '</h6>' +
          '<small class="d-block">' + escapeHtml(notification.message || getAdminNotificationMessage(notification)) + "</small>" +
          (createdTxt ? '<small class="d-block">' + escapeHtml(createdTxt) + "</small>" : "") +
          '</div>' +
          '</div>' +
          '</li>'
        );
      })
      .join("");

    var issueMarkup = issueItems
      .map(function (message) {
        return (
          "<li>" +
          '<div class="timeline-panel">' +
          '<div class="media me-2 media-warning"><i class="fa fa-exclamation-triangle"></i></div>' +
          '<div class="media-body">' +
          '<h6 class="mb-1">Notification sync issue</h6>' +
          '<small class="d-block">' + escapeHtml(message) + "</small>" +
          "</div>" +
          "</div>" +
          "</li>"
        );
      })
      .join("");

    bellNotificationTimeline.innerHTML = issueMarkup + requestItems + adminItems;
    if (bellNotificationBody) {
      bellNotificationBody.style.height = "380px";
    }
  }

  function getAdminNotificationTitle(notification) {
    var notificationType = String(notification && notification.type ? notification.type : "").toUpperCase();
    if (notificationType === "INSCRIPTION_CONFIRMED") return "Inscription accepted";
    if (notificationType === "INSCRIPTION_REFUSED") return "Inscription refused";
    if (notificationType === "WAITLIST_PROMOTION") return "Waitlist promoted";
    if (notificationType === "EVENT_CREATED") return "New event published";
    return "Admin update";
  }

  function getAdminNotificationMessage(notification) {
    var notificationType = String(notification && notification.type ? notification.type : "").toUpperCase();
    var studentId = notification && notification.studentId ? String(notification.studentId) : "unknown student";
    var eventId = notification && notification.eventId ? String(notification.eventId) : "unknown event";

    if (notificationType === "INSCRIPTION_CONFIRMED") {
      return "Student #" + studentId + " was accepted for event #" + eventId + ".";
    }
    if (notificationType === "INSCRIPTION_REFUSED") {
      return "Student #" + studentId + " was refused for event #" + eventId + ".";
    }
    if (notificationType === "WAITLIST_PROMOTION") {
      return "Student #" + studentId + " moved from the waitlist to confirmed registration for event #" + eventId + ".";
    }
    if (notificationType === "EVENT_CREATED") {
      return "A new event is now available for registration.";
    }
    return "A new notification is available.";
  }

  function renderEventCards(events) {
    if (!listContainer || !listScrollArea) return;
    renderOverviewStats(events);
    if (!events.length) {
      listScrollArea.innerHTML =
        '<div class="yb-empty-state"><div class="yb-empty-state-inner"><span class="yb-empty-kicker">No matches</span><h4>No events found.</h4><p>Try another search or filter to reveal more events.</p></div></div>';
      renderEventNavigation(0);
      return;
    }

    var eventsPerPage = getEventsPerPage();
    var totalPages = Math.max(1, Math.ceil(events.length / eventsPerPage));
    currentPage = Math.min(Math.max(currentPage, 1), totalPages);
    var pageStart = (currentPage - 1) * eventsPerPage;
    var pageEvents = events.slice(pageStart, pageStart + eventsPerPage);

    listScrollArea.innerHTML = pageEvents
      .map(function (ev, visibleIndex) {
        var isTerminated = isEventTerminated(ev);
        var statusText = isTerminated ? "TERMINE" : (ev.statut || "PUBLIE");
        var totalSeats = Number(ev.capacite || 0);
        var registeredSeats = Number(ev.inscriptionsCount || 0);
        var remainingSeats = Math.max(totalSeats - registeredSeats, 0);
        var waitlistCount = getWaitlistCountForEvent(ev.idEvent);
        var fillRate = totalSeats > 0 ? Math.min(100, Math.round((registeredSeats / totalSeats) * 100)) : 0;
        var typeText = escapeHtml(String(ev.type || "EVENT"));
        var accentClass = buildEventAccentClass(ev, isTerminated, visibleIndex);
        var eventImageUrl = ev.imageUrl ? String(ev.imageUrl).trim() : "";
        var imageMarkup = eventImageUrl
          ? '<div class="event-card-visual has-image"><img src="' + escapeHtml(eventImageUrl) + '" alt="' + escapeHtml(ev.name || "Event image") + '" loading="lazy"></div>'
          : '<div class="event-card-visual"><div class="event-card-visual-fallback"><i class="fa fa-image"></i><span>No image</span></div></div>';
        return (
          '<article class="event-card-notification ' + accentClass + '">' +
          imageMarkup +
          '<div class="event-card-main">' +
          '<div class="event-card-topline">' +
          '<span class="event-card-chip">' + typeText + '</span>' +
          '<span class="event-card-date-inline">' + formatDate(ev.dateDebut) + '</span>' +
          (ev.referenceEvent ? '<span class="event-card-reference">' + escapeHtml(ev.referenceEvent) + '</span>' : '') +
          '<span class="event-card-badge' + (isTerminated ? " subtle" : "") + '">' + escapeHtml(statusText) + '</span>' +
          '</div>' +
          '<div class="event-card-title-wrap">' +
          '<h4 class="event-card-title">' + escapeHtml(ev.name || "Event") + '</h4>' +
          '<p class="event-card-description">' + escapeHtml(truncateText(ev.description || "No description available for this event yet.", 140)) + '</p>' +
          '</div>' +
          '<div class="event-card-meta-line">' +
          '<span><i class="fa fa-clock-o"></i>' + formatTimeRange(ev.dateDebut, ev.dateFin) + '</span>' +
          '<span><i class="fa fa-map-marker"></i>' + escapeHtml(ev.location || "Location TBD") + '</span>' +
          '<span><i class="fa fa-users"></i>' + registeredSeats + " / " + totalSeats + ' seats</span>' +
          '<span><i class="fa fa-ticket"></i>' + remainingSeats + ' remaining</span>' +
          '<span><i class="fa fa-hourglass-half"></i>' + waitlistCount + ' waiting</span>' +
          '</div>' +
          '<div class="event-card-summary-row">' +
          '<span class="event-card-summary-pill"><strong>+' + registeredSeats + '</strong> joined</span>' +
          '<span class="event-card-summary-pill"><strong>+' + waitlistCount + '</strong> waitlist</span>' +
          '</div>' +
          '</div>' +
          '<div class="event-card-side">' +
          '<div class="event-card-fill-panel">' +
          '<div class="event-card-fill-head"><span>' + fillRate + '% Fill Rate</span><strong>' + registeredSeats + '/' + totalSeats + '</strong></div>' +
          '<div class="event-card-progress"><span style="width:' + fillRate + '%"></span></div>' +
          '</div>' +
          '<div class="event-card-actions">' +
          '<button type="button" class="btn btn-info light btn-xs event-card-manage-btn" data-action="edit-event" data-id="' + Number(ev.idEvent) + '"' + (isTerminated ? ' disabled style="opacity:.55;cursor:not-allowed;"' : '') + '>Manage Event <i class="fa fa-angle-right"></i></button>' +
          '<div class="event-card-action-icons">' +
          '<button type="button" class="btn btn-primary light btn-xs event-card-icon-btn" title="Edit event" aria-label="Edit event" data-action="edit-event" data-id="' + Number(ev.idEvent) + '"' + (isTerminated ? ' disabled style="opacity:.55;cursor:not-allowed;"' : '') + '><i class="fa fa-pencil"></i></button>' +
          '<button type="button" class="btn btn-info light btn-xs event-card-icon-btn" title="View waitlist" aria-label="View waitlist" data-action="view-waitlist" data-id="' + Number(ev.idEvent) + '"><svg viewBox="0 0 24 24" aria-hidden="true" focusable="false"><circle cx="5" cy="7" r="1.25"></circle><circle cx="5" cy="12" r="1.25"></circle><circle cx="5" cy="17" r="1.25"></circle><path d="M9 7h10M9 12h10M9 17h10"></path></svg></button>' +
          '<button type="button" class="btn btn-warning light btn-xs event-card-icon-btn" title="' + (isTerminated ? "Archived event" : "Archive event") + '" aria-label="' + (isTerminated ? "Archived event" : "Archive event") + '" data-action="archive-event" data-id="' + Number(ev.idEvent) + '"' + (isTerminated ? ' disabled style="opacity:.55;cursor:not-allowed;"' : '') + '><i class="fa ' + (isTerminated ? 'fa-check-circle' : 'fa-archive') + '"></i></button>' +
          '</div>' +
          "</div>" +
          "</div>" +
          "</article>"
        );
      })
      .join("");

    renderEventNavigation(events.length, totalPages);
  }

  function initEventFilters() {
    if (eventSortFilter) {
      eventSortFilter.addEventListener("change", applyEventFiltersAndRender);
    }
    if (eventStatusFilter) {
      eventStatusFilter.addEventListener("change", applyEventFiltersAndRender);
    }
    if (eventDateFilter) {
      eventDateFilter.addEventListener("change", applyEventFiltersAndRender);
    }
    if (eventSearchTop) {
      eventSearchTop.addEventListener("input", applyEventFiltersAndRender);
    }
  }

  function initAnalyticsCards() {
    if (!overviewRangeTabs) return;

    overviewRangeTabs.addEventListener("click", function (event) {
      var trigger = event.target.closest("[data-series]");
      if (!trigger) return;

      eventOverviewRange = trigger.getAttribute("data-series") || "week";
      Array.from(overviewRangeTabs.querySelectorAll(".nav-link")).forEach(function (button) {
        button.classList.toggle("active", button === trigger);
      });
      renderAnalyticsCharts();
    });
  }

  function refreshTopBarSelects() {
    if (!(window.jQuery && window.jQuery.fn && window.jQuery.fn.selectpicker)) {
      return;
    }

    [
      eventSortFilter,
      eventStatusFilter,
      eventDateFilter,
      monthFilter,
      yearFilter
    ].forEach(function (element) {
      if (element) {
        window.jQuery(element).selectpicker("refresh");
      }
    });
  }

  function getWaitlistCountForEvent(eventId) {
    return pendingInscriptionsCache.filter(function (ins) {
      return Number(ins.idEvent) === Number(eventId) &&
        String(ins.statut || "").toUpperCase() === "LISTE_ATTENTE";
    }).length;
  }

  async function openWaitlistModal(eventId, eventName) {
    if (!waitlistModalEl || !waitlistModalTitle || !waitlistModalBody || !waitlistModal) {
      return;
    }

    waitlistModalTitle.textContent = "Waitlist - " + (eventName || ("Event #" + eventId));
    if (waitlistModalMeta) {
      waitlistModalMeta.textContent = "Loading the current waiting queue...";
    }
    waitlistModalBody.innerHTML =
      '<div class="yb-waitlist-loading">' +
      '<span class="yb-waitlist-loading-orb"></span>' +
      '<div><strong>Refreshing waitlist</strong><p>Gathering the oldest waiting students for this event.</p></div>' +
      '</div>';
    waitlistModal.show();

    try {
      var entries = await requestInscriptionApi("/event/" + Number(eventId) + "/waitlist");
      renderWaitlistModal(eventId, eventName, Array.isArray(entries) ? entries : []);
    } catch (error) {
      console.error(error);
      if (waitlistModalMeta) {
        waitlistModalMeta.textContent = "The waitlist could not be loaded right now.";
      }
      waitlistModalBody.innerHTML =
        '<div class="yb-waitlist-empty">' +
        '<div class="yb-waitlist-empty-icon"><i class="fa fa-exclamation-circle"></i></div>' +
        '<h5>Unable to load the queue</h5>' +
        '<p>' + escapeHtml(error.message || "Unknown error") + '</p>' +
        '</div>';
    }
  }

  function renderWaitlistModal(eventId, eventName, entries) {
    if (!waitlistModalBody) return;

    if (waitlistModalMeta) {
      waitlistModalMeta.textContent = entries.length
        ? entries.length + " student" + (entries.length > 1 ? "s" : "") + " currently waiting for a seat."
        : "No students are currently waiting for this event.";
    }

    if (!entries.length) {
      waitlistModalBody.innerHTML =
        '<div class="yb-waitlist-empty">' +
        '<div class="yb-waitlist-empty-icon"><i class="fa fa-hourglass-end"></i></div>' +
        '<h5>No active waitlist</h5>' +
        '<p>' + escapeHtml(eventName || ("Event #" + eventId)) + ' has no students waiting at the moment.</p>' +
        '</div>';
      return;
    }

    waitlistModalBody.innerHTML =
      '<div class="yb-waitlist-hero">' +
      '<div><span class="yb-waitlist-kicker">Seat priority</span><h4>The next available place goes to the first card below.</h4></div>' +
      '<span class="yb-waitlist-pill">' + entries.length + ' queued</span>' +
      '</div>' +
      entries.map(function (entry, index) {
        var joinedText = entry.dateInscription ? formatDate(entry.dateInscription) + " - " + formatTime(entry.dateInscription) : "Unknown join date";
        return (
          '<article class="yb-waitlist-entry' + (index === 0 ? ' is-next-up' : '') + '">' +
          '<div class="yb-waitlist-rank">' + (index + 1) + '</div>' +
          '<div class="yb-waitlist-copy">' +
          '<div class="yb-waitlist-copy-top">' +
          '<h5>' + escapeHtml(entry.studentName || ("Student #" + entry.idStudent)) + '</h5>' +
          '<span class="yb-waitlist-status">Waitlist</span>' +
          '</div>' +
          '<p>Student ID ' + escapeHtml(String(entry.idStudent)) + (entry.studentEmail ? ' · ' + escapeHtml(entry.studentEmail) : '') + '</p>' +
          '<div class="yb-waitlist-meta">' +
          '<span><i class="fa fa-clock-o"></i>Joined ' + escapeHtml(joinedText) + '</span>' +
          '<span><i class="fa fa-bolt"></i>' + (index === 0 ? 'First to be promoted when a seat opens' : 'Waiting in chronological order') + '</span>' +
          '</div>' +
          '</div>' +
          '</article>'
        );
      }).join("");
  }

  function applyEventFiltersAndRender() {
    var eventsToRender = getFilteredAndSortedEvents();
    currentPage = 1;
    refreshCalendar(eventsToRender);
    renderEventCards(eventsToRender);
    if (eventFilterInfo) {
      eventFilterInfo.textContent = eventsToRender.length + " event(s) shown";
    }
  }

  function getFilteredAndSortedEvents() {
    var statusValue = eventStatusFilter ? eventStatusFilter.value : "ALL";
    var dateValue = eventDateFilter ? eventDateFilter.value : "ALL";
    var sortValue = eventSortFilter ? eventSortFilter.value : "date_asc";
    var searchValue = eventSearchTop ? eventSearchTop.value.trim().toLowerCase() : "";
    var today = new Date();
    today.setHours(0, 0, 0, 0);

    var filtered = eventsCache.filter(function (ev) {
      var status = isEventTerminated(ev) ? "TERMINE" : String(ev.statut || "PUBLIE").toUpperCase();
      var eventDate = ev.dateDebut ? new Date(String(ev.dateDebut).replace(" ", "T")) : null;
      var eventDay = eventDate && !Number.isNaN(eventDate.getTime()) ? new Date(eventDate) : null;
      if (eventDay) {
        eventDay.setHours(0, 0, 0, 0);
      }

      if (statusValue !== "ALL" && status !== statusValue) return false;
      if (dateValue === "TODAY" && (!eventDay || eventDay.getTime() !== today.getTime())) return false;
      if (dateValue === "UPCOMING" && (!eventDay || eventDay < today)) return false;
      if (dateValue === "PAST" && (!eventDay || eventDay >= today)) return false;
      if (searchValue) {
        var haystack = (
          (ev.name || "") + " " +
          (ev.description || "") + " " +
          (ev.location || "") + " " +
          (status || "")
        ).toLowerCase();
        if (!haystack.includes(searchValue)) return false;
      }
      return true;
    });

    filtered.sort(function (a, b) {
      var da = a.dateDebut ? new Date(String(a.dateDebut).replace(" ", "T")).getTime() : 0;
      var db = b.dateDebut ? new Date(String(b.dateDebut).replace(" ", "T")).getTime() : 0;
      return sortValue === "date_desc" ? db - da : da - db;
    });

    return filtered;
  }

  function resetFormForCreate() {
    if (descriptionGenerationTimer) {
      clearTimeout(descriptionGenerationTimer);
      descriptionGenerationTimer = null;
    }
    if (imageGenerationTimer) {
      clearTimeout(imageGenerationTimer);
      imageGenerationTimer = null;
    }
    activeDescriptionRequestId += 1;
    activeImageRequestId += 1;
    form.reset();
    document.getElementById("eventId").value = "";
    document.getElementById("eventStatut").value = "PUBLIE";
    modalTitle.textContent = "New Event";
    if (eventDescriptionInput) {
      eventDescriptionInput.dataset.manualEdit = "false";
    }
    lastGeneratedDescription = "";
    lastGeneratedImageUrl = "";
    imageSourceMode = "idle";
    if (eventImageFileInput) {
      eventImageFileInput.value = "";
    }
    updateDescriptionHint("AI ready to draft the paragraph from the event name.", "idle");
    setEventImage("", "AI ready to prepare the event visual from the event name and description.", "idle");
    clearLocationResults();
    updateLocationStatus("Map ready for search and selection.");
    clearLocationMarker(false);
    applyDateInputBounds();
  }

  function fillFormForEdit(ev) {
    if (isEventTerminated(ev)) {
      showStyledMessage("This event is terminated and can no longer be modified.");
      return;
    }
    if (descriptionGenerationTimer) {
      clearTimeout(descriptionGenerationTimer);
      descriptionGenerationTimer = null;
    }
    if (imageGenerationTimer) {
      clearTimeout(imageGenerationTimer);
      imageGenerationTimer = null;
    }
    activeDescriptionRequestId += 1;
    activeImageRequestId += 1;
    document.getElementById("eventId").value = ev.idEvent || "";
    document.getElementById("eventName").value = ev.name || "";
    document.getElementById("eventDescription").value = ev.description || "";
    if (eventImageUrlInput) {
      eventImageUrlInput.value = ev.imageUrl || "";
    }
    document.getElementById("eventLocation").value = ev.location || "";
    document.getElementById("eventCapacity").value = ev.capacite ?? "";
    document.getElementById("eventDateDebut").value = toDateTimeLocalValue(ev.dateDebut);
    document.getElementById("eventDateFin").value = toDateTimeLocalValue(ev.dateFin);
    document.getElementById("eventType").value = ev.type || "";
    document.getElementById("eventStatut").value = ev.statut || "";
    modalTitle.textContent = "Edit Schedule";
    if (eventDescriptionInput) {
      eventDescriptionInput.dataset.manualEdit = "true";
    }
    lastGeneratedDescription = document.getElementById("eventDescription").value || "";
    lastGeneratedImageUrl = ev.imageUrl || "";
    imageSourceMode = ev.imageUrl ? "manual" : "idle";
    if (eventImageFileInput) {
      eventImageFileInput.value = "";
    }
    updateDescriptionHint("Editing mode keeps the existing description unless you change it manually.", "idle");
    setEventImage(ev.imageUrl || "", ev.imageUrl ? "Existing event image loaded." : "No saved image for this event yet.", "idle");
    clearLocationResults();
    updateLocationStatus(ev.location ? "Loaded saved event location." : "Search a venue name or click on the map.");
    locateExistingEventLocation(ev.location || "");
    applyDateInputBounds();
    if (modal) {
      modal.show();
    }
  }

  function buildPayload() {
    var session = {};
    try { session = JSON.parse(localStorage.getItem('bb_user_session_v1') || '{}'); } catch(e) {}
    return {
      adminId: session.userId || session.idUser || 0,
      name: document.getElementById("eventName").value.trim(),
      description: document.getElementById("eventDescription").value.trim(),
      imageUrl: eventImageUrlInput ? eventImageUrlInput.value.trim() : "",
      location: document.getElementById("eventLocation").value.trim(),
      capacite: Number(document.getElementById("eventCapacity").value),
      dateDebut: normalizeDateTime(document.getElementById("eventDateDebut").value),
      dateFin: normalizeDateTime(document.getElementById("eventDateFin").value),
      type: document.getElementById("eventType").value,
      statut: document.getElementById("eventStatut").value
    };
  }

  function validateFormInputs() {
    var name = document.getElementById("eventName").value.trim();
    var description = document.getElementById("eventDescription").value.trim();
    var location = document.getElementById("eventLocation").value.trim();
    var capacity = Number(document.getElementById("eventCapacity").value);
    var dateDebut = document.getElementById("eventDateDebut").value;
    var dateFin = document.getElementById("eventDateFin").value;
    var type = document.getElementById("eventType").value;
    var statut = document.getElementById("eventStatut").value;

    if (!name) return "Please enter Event Name.";
    if (!description) return "Please enter Description.";
    if (description.length > 5000) return "Description is too long. Please keep it under 5000 characters.";
    if (!location) return "Please enter Location.";
    if (!capacity || capacity <= 0) return "Please enter a valid Capacity.";
    if (!dateDebut) return "Please select Start Date.";
    if (!dateFin) return "Please select End Date.";
    if (!type) return "Please select Type.";
    if (!statut) return "Please select Status.";
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var startDay = new Date(dateDebut);
    startDay.setHours(0, 0, 0, 0);
    var endDay = new Date(dateFin);
    endDay.setHours(0, 0, 0, 0);
    if (startDay < today) return "Start Date cannot be before today.";
    if (endDay < today) return "End Date cannot be before today.";
    if (new Date(dateFin) < new Date(dateDebut)) return "End Date must be after Start Date.";
    return null;
  }

  function applyDateInputBounds() {
    if (!startDateInput || !endDateInput) return;
    var todayMin = startOfTodayLocalValue();
    startDateInput.min = todayMin;
    endDateInput.min = startDateInput.value && startDateInput.value > todayMin ? startDateInput.value : todayMin;
  }

  function enforceDateNotBeforeToday(input, label) {
    if (!input || !input.value) return;
    var selected = new Date(input.value);
    selected.setHours(0, 0, 0, 0);
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    if (selected < today) {
      showStyledMessage(label + " cannot be before today. Please choose a valid date.");
      input.value = startOfTodayLocalValue();
    }
  }

  function enforceEndAfterStart() {
    if (!startDateInput || !endDateInput) return;
    if (!startDateInput.value || !endDateInput.value) return;
    var start = new Date(startDateInput.value);
    var end = new Date(endDateInput.value);
    if (end < start) {
      showStyledMessage("End Date cannot be before Start Date. It has been adjusted.");
      endDateInput.value = startDateInput.value;
    }
  }

  function startOfTodayLocalValue() {
    var d = new Date();
    d.setHours(0, 0, 0, 0);
    return toDateTimeLocalValue(d);
  }

  async function requestApi(path, options) {
    var config = Object.assign(
      {
        headers: { "Content-Type": "application/json" }
      },
      options || {}
    );
    if (config.body instanceof FormData && config.headers) {
      delete config.headers["Content-Type"];
    }
    var url = API_BASE + path;
    var response = await fetch(url, config);
    if (!response.ok) {
      var errorText = "";
      try {
        errorText = (await response.text()).trim();
      } catch (_) {
        errorText = "";
      }
      throw new Error("HTTP " + response.status + " on " + url + (errorText ? " - " + errorText : ""));
    }
    var contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
      return response.json();
    }
    return null;
  }

  async function requestInscriptionApi(path, options) {
    var config = Object.assign(
      {
        headers: { "Content-Type": "application/json" }
      },
      options || {}
    );
    var url = INSCRIPTION_API_BASE + path;
    var response = await fetch(url, config);
    if (!response.ok) {
      var errorText = "";
      try {
        errorText = (await response.text()).trim();
      } catch (_) {
        errorText = "";
      }
      throw new Error("HTTP " + response.status + " on " + url + (errorText ? " - " + errorText : ""));
    }
    var contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
      return response.json();
    }
    return null;
  }

  function toDateTimeLocalValue(value) {
    if (!value) return "";
    var raw = String(value).trim().replace(" ", "T");
    return raw.length >= 16 ? raw.slice(0, 16) : raw;
  }

  function normalizeDateTime(value) {
    if (!value) return null;
    return value.length === 16 ? value + ":00" : value;
  }

  function formatDate(value) {
    if (!value) return "-";
    var date = new Date(String(value).replace(" ", "T"));
    if (Number.isNaN(date.getTime())) return escapeHtml(String(value));
    return date.toLocaleDateString();
  }

  function formatTimeRange(start, end) {
    var startDate = start ? new Date(String(start).replace(" ", "T")) : null;
    var endDate = end ? new Date(String(end).replace(" ", "T")) : null;
    if (!startDate || Number.isNaN(startDate.getTime())) return "-";
    var startText = startDate.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    if (!endDate || Number.isNaN(endDate.getTime())) return startText;
    var endText = endDate.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
    return startText + " - " + endText;
  }

  function formatTime(value) {
    if (!value) return "";
    var date = new Date(String(value).replace(" ", "T"));
    if (Number.isNaN(date.getTime())) return "";
    return date.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" });
  }

  function getMonthShortLabel(value) {
    if (!value) return "N/A";
    var date = new Date(String(value).replace(" ", "T"));
    if (Number.isNaN(date.getTime())) return "N/A";
    return date.toLocaleString([], { month: "short" }).toUpperCase();
  }

  function getDayOfMonth(value) {
    if (!value) return "--";
    var date = new Date(String(value).replace(" ", "T"));
    if (Number.isNaN(date.getTime())) return "--";
    return String(date.getDate()).padStart(2, "0");
  }

  function getEventsPerPage() {
    var width = window.innerWidth || document.documentElement.clientWidth || 1280;
    if (width >= 1700) return 6;
    if (width >= 1280) return 5;
    return 4;
  }

  function buildEventAccentClass(ev, isTerminated, visibleIndex) {
    var type = String((ev && ev.type) || "EVENT").toUpperCase();
    var classes = [];
    if (isTerminated) {
      classes.push("is-terminated");
    } else if (visibleIndex === 0 && currentPage === 1) {
      classes.push("is-featured");
    }

    if (type === "HACKATHON") classes.push("type-hackathon");
    else if (type === "WEBINAIRE") classes.push("type-webinaire");
    else if (type === "ATELIER") classes.push("type-atelier");
    else if (type === "FORMATION") classes.push("type-formation");
    else classes.push("type-default");

    return classes.join(" ");
  }

  function escapeHtml(value) {
    return String(value)
      .replaceAll("&", "&amp;")
      .replaceAll("<", "&lt;")
      .replaceAll(">", "&gt;")
      .replaceAll('"', "&quot;")
      .replaceAll("'", "&#039;");
  }

  function truncateText(value, maxLength) {
    var text = String(value || "").trim();
    if (!text) return "";
    if (text.length <= maxLength) return text;
    return text.slice(0, Math.max(0, maxLength - 1)).trimEnd() + "…";
  }

  function showStyledMessage(message) {
    if (!msgOverlay || !msgText) {
      alert(message);
      return;
    }
    msgText.textContent = message;
    msgOverlay.classList.add("show");
  }

  function hideStyledMessage() {
    if (!msgOverlay) return;
    msgOverlay.classList.remove("show");
  }

  function syncDescriptionFromEventName() {
    if (!eventNameInput || !eventDescriptionInput) return Promise.resolve();
    return syncDescriptionFromEventNameInternal(eventNameInput.value.trim());
  }

  function syncDescriptionFromEventNameDebounced() {
    if (!eventNameInput || !eventDescriptionInput) return;
    if (descriptionGenerationTimer) {
      clearTimeout(descriptionGenerationTimer);
    }
    descriptionGenerationTimer = setTimeout(function () {
      syncDescriptionFromEventName();
    }, 650);
  }

  function syncImageFromEventContentDebounced() {
    if (imageSourceMode === "uploaded" && eventImageUrlInput && eventImageUrlInput.value.trim()) {
      return;
    }
    if (imageGenerationTimer) {
      clearTimeout(imageGenerationTimer);
    }
    imageGenerationTimer = setTimeout(function () {
      syncImageFromEventContent();
    }, 850);
  }

  async function syncDescriptionFromEventNameInternal(name) {
    var isUpdate = Boolean(document.getElementById("eventId").value);
    if (isUpdate) return;

    var manualEdit = eventDescriptionInput.dataset.manualEdit === "true";
    if (manualEdit && eventDescriptionInput.value && eventDescriptionInput.value !== lastGeneratedDescription) {
      return;
    }

    if (!name) {
      eventDescriptionInput.value = "";
      lastGeneratedDescription = "";
      updateDescriptionHint("AI ready to draft the paragraph from the event name.", "idle");
      return;
    }

    var requestId = activeDescriptionRequestId + 1;
    activeDescriptionRequestId = requestId;
    updateDescriptionHint("AI is composing a polished event paragraph...", "generating");

    var generated = await generateDescriptionWithAi(name);
    if (requestId !== activeDescriptionRequestId) {
      return;
    }

    eventDescriptionInput.value = generated;
    eventDescriptionInput.dataset.manualEdit = "false";
    lastGeneratedDescription = generated;
    syncImageFromEventContent();
    updateDescriptionHint(
      generateDescriptionWithAi.lastUsedAi
        ? "Groq draft ready. You can refine the paragraph manually if you want."
        : "You can refine the paragraph manually if you want.",
      generateDescriptionWithAi.lastUsedAi ? "success" : "warning"
    );
  }

  async function generateDescriptionWithAi(name) {
    try {
      var response = await requestApi(DESCRIPTION_API_PATH, {
        method: "POST",
        body: JSON.stringify({
          name: name,
          type: getSelectedEventType()
        })
      });

      var generated = response && response.description ? String(response.description).trim() : "";

      if (!generated) {
        throw new Error("Empty generated description");
      }

      generated = generated.replace(/^["']+|["']+$/g, "").trim();
      if (generated.length > 5000) {
        generated = generated.slice(0, 4997).trimEnd() + "...";
      }
      generateDescriptionWithAi.lastUsedAi = Boolean(response && response.generatedByAi);
      return generated;
    } catch (error) {
      console.warn("AI description generation failed, using fallback.", error);
      updateDescriptionHint("AI is unavailable right now. Using the smart local fallback instead.", "warning");
      generateDescriptionWithAi.lastUsedAi = false;
      return generateDescriptionFromName(name);
    }
  }

  async function syncImageFromEventContent() {
    var eventIdField = document.getElementById("eventId");
    if (eventIdField && eventIdField.value) {
      return;
    }

    var name = eventNameInput ? eventNameInput.value.trim() : "";
    var description = eventDescriptionInput ? eventDescriptionInput.value.trim() : "";
    if (imageSourceMode === "uploaded" && eventImageUrlInput && eventImageUrlInput.value.trim()) {
      return;
    }
    if (!name) {
      lastGeneratedImageUrl = "";
      imageSourceMode = "idle";
      setEventImage("", "AI ready to prepare the event visual from the event name and description.", "idle");
      return;
    }

    var requestId = activeImageRequestId + 1;
    activeImageRequestId = requestId;
    updateImageHint("AI is preparing the event visual...", "generating");

    try {
      var response = await requestApi(IMAGE_API_PATH, {
        method: "POST",
        body: JSON.stringify({
          name: name,
          description: description,
          type: getSelectedEventType()
        })
      });

      if (requestId !== activeImageRequestId) {
        return;
      }

      var imageUrl = response && response.imageUrl ? String(response.imageUrl).trim() : "";
      lastGeneratedImageUrl = imageUrl;
      imageSourceMode = imageUrl ? "generated" : "idle";
      setEventImage(
        imageUrl,
        response && response.generatedByAi
          ? "AI event image ready. It will be saved with the event."
          : "Image prepared and ready to save with the event.",
        response && response.generatedByAi ? "success" : "warning"
      );
    } catch (error) {
      if (requestId !== activeImageRequestId) {
        return;
      }
      lastGeneratedImageUrl = "";
      imageSourceMode = "idle";
      setEventImage("", "AI image generation is unavailable right now.", "warning");
    }
  }

  async function uploadSelectedEventImage() {
    if (!eventImageFileInput || !eventImageFileInput.files || !eventImageFileInput.files.length) {
      return;
    }

    var file = eventImageFileInput.files[0];
    var formData = new FormData();
    formData.append("image", file);
    updateImageHint("Uploading your image...", "generating");

    try {
      var response = await requestApi(IMAGE_UPLOAD_API_PATH, {
        method: "POST",
        body: formData
      });

      var imageUrl = response && response.imageUrl ? String(response.imageUrl).trim() : "";
      lastGeneratedImageUrl = "";
      imageSourceMode = imageUrl ? "uploaded" : "idle";
      setEventImage(
        imageUrl,
        imageUrl
          ? "Uploaded image ready. It will be saved with the event."
          : "Upload finished, but no image URL was returned.",
        imageUrl ? "success" : "warning"
      );
    } catch (error) {
      console.error(error);
      imageSourceMode = eventImageUrlInput && eventImageUrlInput.value.trim() ? imageSourceMode : "idle";
      updateImageHint("Image upload failed. Please try another file.", "warning");
      showStyledMessage("Image upload failed: " + error.message);
    } finally {
      eventImageFileInput.value = "";
    }
  }

  function getSelectedEventType() {
    var typeField = document.getElementById("eventType");
    return typeField ? typeField.value : "";
  }

  function initLocationPicker() {
    if (!eventLocationMapEl || !window.maplibregl) return;

    locationMap = new window.maplibregl.Map({
      container: eventLocationMapEl,
      style: "https://tiles.openfreemap.org/styles/liberty",
      center: [10.1815, 36.8065],
      zoom: 5,
      attributionControl: true
    });

    locationMap.addControl(new window.maplibregl.NavigationControl({ visualizePitch: false }), "top-right");

    locationMap.on("click", function (event) {
      reverseGeocodeLocation(event.lngLat.lat, event.lngLat.lng);
    });

    locationMap.on("load", function () {
      refreshLocationMapLayout(true);
    });

    if (modalEl) {
      modalEl.addEventListener("shown.bs.modal", function () {
        refreshLocationMapLayout(true);
        setTimeout(function () {
          refreshLocationMapLayout(true);
        }, 220);
        setTimeout(function () {
          refreshLocationMapLayout(true);
        }, 520);
      });
    }
  }

  function refreshLocationMapLayout(recenterDefault) {
    if (!locationMap) return;
    locationMap.resize();
    if (locationMarker) {
      locationMap.easeTo({ center: locationMarker.getLngLat(), duration: 0, zoom: Math.max(locationMap.getZoom(), 14) });
    } else if (recenterDefault) {
      locationMap.easeTo({ center: [10.1815, 36.8065], zoom: 5, duration: 0 });
    }
  }

  async function performLocationSearch() {
    if (!eventLocationInput) return;
    var query = eventLocationInput.value.trim();
    if (query.length < 3) {
      updateLocationStatus("Type at least 3 characters to search for a place.");
      renderLocationResults([]);
      return;
    }

    updateLocationStatus("Searching places on the map...");

    try {
      var response = await fetch(
        "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=5&q=" + encodeURIComponent(query)
      );
      if (!response.ok) {
        throw new Error("Location search HTTP " + response.status);
      }
      var results = await response.json();
      renderLocationResults(Array.isArray(results) ? results : []);
      if (!results || !results.length) {
        updateLocationStatus("No matching place found. Try a more specific search.");
      } else {
        updateLocationStatus("Choose a result or refine the place directly on the map.");
      }
    } catch (error) {
      console.error(error);
      renderLocationResults([]);
      updateLocationStatus("Map search is unavailable right now.");
    }
  }

  function renderLocationResults(results) {
    if (!eventLocationResults) return;
    if (!results || !results.length) {
      eventLocationResults.innerHTML = '<div class="yb-location-empty">No place selected yet. Search for a venue or click directly on the map to pin one.</div>';
      eventLocationResults.classList.remove("show");
      return;
    }

    eventLocationResults.innerHTML = results.map(function (item) {
      return (
        '<button type="button" class="yb-location-result" ' +
        'data-lat="' + escapeHtml(item.lat || "") + '" ' +
        'data-lon="' + escapeHtml(item.lon || "") + '" ' +
        'data-name="' + escapeHtml(item.display_name || "") + '">' +
        escapeHtml(item.display_name || "Unknown place") +
        "</button>"
      );
    }).join("");
    eventLocationResults.classList.add("show");
  }

  document.addEventListener("click", function (event) {
    var locationResult = event.target.closest(".yb-location-result");
    if (!locationResult) return;

    var lat = Number(locationResult.getAttribute("data-lat"));
    var lon = Number(locationResult.getAttribute("data-lon"));
    var name = locationResult.getAttribute("data-name") || "";
    applySelectedLocation(lat, lon, name);
  });

  async function reverseGeocodeLocation(lat, lon) {
    updateLocationStatus("Resolving the selected map point...");
    try {
      var response = await fetch(
        "https://nominatim.openstreetmap.org/reverse?format=jsonv2&lat=" +
          encodeURIComponent(String(lat)) +
          "&lon=" +
          encodeURIComponent(String(lon))
      );
      if (!response.ok) {
        throw new Error("Reverse geocoding HTTP " + response.status);
      }
      var result = await response.json();
      var label = result && result.display_name
        ? result.display_name
        : ("Pinned location (" + lat.toFixed(5) + ", " + lon.toFixed(5) + ")");
      applySelectedLocation(lat, lon, label);
    } catch (error) {
      console.error(error);
      applySelectedLocation(lat, lon, "Pinned location (" + lat.toFixed(5) + ", " + lon.toFixed(5) + ")");
      updateLocationStatus("Pinned the point, but place details could not be loaded.");
    }
  }

  function applySelectedLocation(lat, lon, label) {
    if (eventLocationInput) {
      eventLocationInput.value = label || "";
    }
    clearLocationResults();
    updateLocationStatus("Location selected successfully.");
    if (locationMap && Number.isFinite(lat) && Number.isFinite(lon)) {
      if (!locationMarker) {
        var markerElement = document.createElement("div");
        markerElement.className = "yb-location-pin";
        locationMarker = new window.maplibregl.Marker({ element: markerElement, anchor: "bottom" })
          .setLngLat([lon, lat])
          .addTo(locationMap);
      } else {
        locationMarker.setLngLat([lon, lat]);
      }
      if (locationPopup) {
        locationPopup.remove();
      }
      locationPopup = new window.maplibregl.Popup({
        closeButton: false,
        closeOnClick: false,
        offset: 18
      })
        .setLngLat([lon, lat])
        .setHTML(escapeHtml(label || "Selected location"))
        .addTo(locationMap);
      locationMap.easeTo({ center: [lon, lat], zoom: 14, duration: 450 });
      setTimeout(function () {
        refreshLocationMapLayout(false);
      }, 90);
    }
  }

  function clearLocationResults() {
    if (!eventLocationResults) return;
    eventLocationResults.innerHTML = '<div class="yb-location-empty">Search results will appear here once you start looking for a place.</div>';
    eventLocationResults.classList.remove("show");
  }

  function clearLocationMarker(keepView) {
    if (!locationMap || !locationMarker) return;
    locationMarker.remove();
    locationMarker = null;
    if (locationPopup) {
      locationPopup.remove();
      locationPopup = null;
    }
    if (!keepView) {
      locationMap.easeTo({ center: [10.1815, 36.8065], zoom: 5, duration: 0 });
    }
  }

  async function locateExistingEventLocation(locationText) {
    if (!locationText || !locationMap) {
      clearLocationMarker(false);
      return;
    }

    try {
      var response = await fetch(
        "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&q=" + encodeURIComponent(locationText)
      );
      if (!response.ok) {
        throw new Error("Location search HTTP " + response.status);
      }
      var results = await response.json();
      if (Array.isArray(results) && results.length) {
        applySelectedLocation(Number(results[0].lat), Number(results[0].lon), locationText);
      }
    } catch (error) {
      console.error(error);
      updateLocationStatus("Saved location loaded without map coordinates.");
    }
  }

  function updateLocationStatus(message) {
    if (!eventLocationStatus) return;
    eventLocationStatus.textContent = message;
  }

  function updateDescriptionHint(message, state) {
    if (!eventDescriptionHint) return;
    if (eventDescriptionHintCopy) {
      eventDescriptionHintCopy.textContent = message;
    } else {
      eventDescriptionHint.textContent = message;
    }
    eventDescriptionHint.setAttribute("data-state", state || "idle");
  }

  function updateImageHint(message, state) {
    if (!eventImageHint) return;
    if (eventImageHintCopy) {
      eventImageHintCopy.textContent = message;
    } else {
      eventImageHint.textContent = message;
    }
    eventImageHint.setAttribute("data-state", state || "idle");
  }

  function setEventImage(imageUrl, message, state) {
    if (eventImageUrlInput) {
      eventImageUrlInput.value = imageUrl || "";
    }
    if (eventImagePreview) {
      eventImagePreview.src = imageUrl || "";
    }
    if (eventImagePreviewWrap) {
      eventImagePreviewWrap.classList.toggle("has-image", Boolean(imageUrl));
    }
    updateImageHint(message, state);
  }

  function generateDescriptionFromName(name) {
    if (!name) return "";

    var lower = name.toLowerCase();
    var topic = "professional skills";
    if (lower.includes("hack")) topic = "innovation and rapid problem-solving";
    else if (lower.includes("web")) topic = "modern web technologies";
    else if (lower.includes("data")) topic = "data analysis and decision-making";
    else if (lower.includes("ai") || lower.includes("ia")) topic = "AI fundamentals and practical use cases";
    else if (lower.includes("design")) topic = "creative design and user experience";
    else if (lower.includes("market")) topic = "digital marketing strategies";
    else if (lower.includes("dev")) topic = "software development best practices";
    else if (lower.includes("manage")) topic = "project management and team coordination";
    else if (lower.includes("cloud")) topic = "cloud platforms and deployment";
    else if (lower.includes("cyber") || lower.includes("security")) topic = "cybersecurity awareness and protection";

    var goals = [
      "build strong practical understanding",
      "develop real-world problem-solving habits",
      "improve collaboration and communication",
      "transform theory into hands-on outcomes",
      "gain clear, actionable methods"
    ];
    var formats = [
      "interactive workshops",
      "guided activities",
      "expert demonstrations",
      "collaborative mini-projects",
      "practical case studies"
    ];
    var outcomes = [
      "apply what they learn immediately",
      "produce a concrete result by the end of the session",
      "leave with a clear action plan",
      "strengthen confidence in real scenarios",
      "improve both technical and strategic thinking"
    ];

    var h = nameHash(name);
    var g = goals[h % goals.length];
    var f = formats[(h + 1) % formats.length];
    var o = outcomes[(h + 2) % outcomes.length];

    return (
      "\"" + name + "\" focuses on " + topic + " and helps participants " + g + ". " +
      "Through " + f + ", this event creates an engaging environment where attendees can practice, exchange ideas, and progress effectively. " +
      "By the end, participants should be able to " + o + "."
    );
  }

  function nameHash(text) {
    var hash = 0;
    for (var i = 0; i < text.length; i += 1) {
      hash = (hash * 31 + text.charCodeAt(i)) >>> 0;
    }
    return hash;
  }

  function isEventTerminated(ev) {
    if (!ev) return false;
    if (String(ev.statut || "").toUpperCase() === "TERMINE") return true;
    if (!ev.dateFin) return false;
    var end = new Date(String(ev.dateFin).replace(" ", "T"));
    return !Number.isNaN(end.getTime()) && end < new Date();
  }

  function initMonthYearFilters() {
    if (!monthFilter || !yearFilter) return;

    var currentYear = new Date().getFullYear();
    var options = ['<option value="">Year</option>'];
    for (var y = currentYear - 5; y <= currentYear + 5; y += 1) {
      options.push('<option value="' + y + '">' + y + '</option>');
    }
    yearFilter.innerHTML = options.join("");
    refreshTopBarSelects();

    monthFilter.addEventListener("change", function () {
      navigateCalendarFromFilters();
    });
    yearFilter.addEventListener("change", function () {
      navigateCalendarFromFilters();
    });

    syncMonthYearFilterWithCalendar();
  }

  function navigateCalendarFromFilters() {
    if (!monthFilter || !yearFilter) return;
    var selectedMonth = monthFilter.value;
    var selectedYear = yearFilter.value;
    if (selectedMonth === "" || selectedYear === "") return;
    var targetDate = new Date(Number(selectedYear), Number(selectedMonth), 1);
    calendar.gotoDate(targetDate);
  }

  function syncMonthYearFilterWithCalendar() {
    if (!monthFilter || !yearFilter) return;
    var activeDate = calendar.getDate();
    monthFilter.value = String(activeDate.getMonth());
    yearFilter.value = String(activeDate.getFullYear());
    refreshTopBarSelects();
  }

  function updateCompactCalendarMeta(events) {
    var activeDate = calendar.getDate();
    if (compactCalendarMonth) {
      compactCalendarMonth.textContent = activeDate.toLocaleString([], {
        month: "long",
        year: "numeric"
      }).toUpperCase();
    }

    if (calendarTodaySummary) {
      var sourceEvents = Array.isArray(events) ? events : getFilteredAndSortedEvents();
      var today = new Date();
      today.setHours(0, 0, 0, 0);
      var todayCount = sourceEvents.filter(function (ev) {
        if (!ev || !ev.dateDebut) return false;
        var eventDate = new Date(String(ev.dateDebut).replace(" ", "T"));
        if (Number.isNaN(eventDate.getTime())) return false;
        eventDate.setHours(0, 0, 0, 0);
        return eventDate.getTime() === today.getTime();
      }).length;
      calendarTodaySummary.innerHTML =
        '<span class="yb-calendar-today-dot"></span><span>' + todayCount + ' Event' + (todayCount === 1 ? '' : 's') + ' Today</span>';
    }
  }

  function renderEventNavigation(totalItems, totalPages) {
    if (!paginationBar) return;

    if (!totalItems) {
      paginationBar.innerHTML = "";
      return;
    }

    var publishedCount = eventsCache.filter(function (ev) {
      return !isEventTerminated(ev);
    }).length;
    var today = new Date();
    today.setHours(0, 0, 0, 0);
    var todayCount = eventsCache.filter(function (ev) {
      if (!ev || !ev.dateDebut) return false;
      var eventDate = new Date(String(ev.dateDebut).replace(" ", "T"));
      if (Number.isNaN(eventDate.getTime())) return false;
      eventDate.setHours(0, 0, 0, 0);
      return eventDate.getTime() === today.getTime();
    }).length;

    paginationBar.innerHTML =
      '<div class="yb-pagination-copy">Showing <strong>' + totalItems + '</strong> filtered events</div>' +
      '<div class="yb-pagination-controls">' +
      '<div class="yb-carousel-progress"><span>Live Schedule</span><strong>' + publishedCount + ' active</strong></div>' +
      '<div class="yb-carousel-progress"><span>Today</span><strong>' + todayCount + ' events</strong></div>' +
      '<div class="yb-page-switcher">' +
      '<button type="button" class="yb-page-arrow" data-page-nav="prev"' + (currentPage <= 1 ? ' disabled' : '') + ' aria-label="Previous page">‹</button>' +
      '<span class="yb-page-indicator">Page ' + currentPage + ' / ' + totalPages + '</span>' +
      '<button type="button" class="yb-page-arrow" data-page-nav="next"' + (currentPage >= totalPages ? ' disabled' : '') + ' aria-label="Next page">›</button>' +
      '</div>' +
      '</div>';

    Array.from(paginationBar.querySelectorAll("[data-page-nav]")).forEach(function (button) {
      button.addEventListener("click", function () {
        var direction = button.getAttribute("data-page-nav");
        currentPage = direction === "prev" ? currentPage - 1 : currentPage + 1;
        renderEventCards(getFilteredAndSortedEvents());
      });
    });
  }

  async function renderAnalyticsCharts() {
    var requestId = ++latestAnalyticsRequestId;

    try {
      var analytics = await requestApi("/analytics?range=" + encodeURIComponent(eventOverviewRange));
      if (requestId !== latestAnalyticsRequestId) {
        return;
      }
      renderEventTrendChart(analytics && analytics.trend ? analytics.trend : null);
      renderEventOverviewChart(analytics && analytics.overview ? analytics.overview : null);
    } catch (error) {
      if (requestId !== latestAnalyticsRequestId) {
        return;
      }
      renderEventTrendChart(null);
      renderEventOverviewChart(null);
    }
  }

  function renderEventTrendChart(trend) {
    var chartEl = document.querySelector("#ybEventTrendChart");
    if (!chartEl || !window.ApexCharts) return;

    if (eventTrendCurrentEl) {
      eventTrendCurrentEl.textContent = String(trend && typeof trend.currentTotal !== "undefined" ? trend.currentTotal : 0);
    }
    if (eventTrendPreviousEl) {
      eventTrendPreviousEl.textContent = String(trend && typeof trend.previousTotal !== "undefined" ? trend.previousTotal : 0);
    }

    var options = {
      series: [
        { name: trend && trend.currentLabel ? trend.currentLabel : "Current", type: "area", data: trend && trend.currentData ? trend.currentData : [0, 0, 0, 0, 0, 0] },
        { name: trend && trend.previousLabel ? trend.previousLabel : "Previous", type: "line", data: trend && trend.previousData ? trend.previousData : [0, 0, 0, 0, 0, 0] }
      ],
      chart: {
        height: 320,
        type: "line",
        toolbar: { show: false },
        zoom: { enabled: false }
      },
      stroke: {
        width: [4, 4],
        curve: "smooth"
      },
      colors: ["var(--primary)", "#ff7a59"],
      fill: {
        type: "solid",
        opacity: [0.12, 1]
      },
      dataLabels: { enabled: false },
      legend: { show: false },
      grid: {
        borderColor: "rgba(120, 131, 166, 0.16)",
        strokeDashArray: 4
      },
      xaxis: {
        categories: trend && trend.labels ? trend.labels : ["Week 01", "Week 02", "Week 03", "Week 04", "Week 05", "Week 06"],
        labels: {
          style: {
            colors: "#888888",
            fontSize: "13px",
            fontFamily: "Poppins"
          }
        },
        axisBorder: { show: false }
      },
      yaxis: {
        min: 0,
        forceNiceScale: true,
        labels: {
          style: {
            colors: "#888888",
            fontSize: "13px",
            fontFamily: "Poppins"
          }
        }
      },
      tooltip: {
        shared: true,
        intersect: false
      }
    };

    if (eventTrendChart) {
      eventTrendChart.destroy();
    }
    eventTrendChart = new ApexCharts(chartEl, options);
    eventTrendChart.render();
  }

  function renderEventOverviewChart(overview) {
    var chartEl = document.querySelector("#ybEventOverviewChart");
    if (!chartEl || !window.ApexCharts) return;
    var options = {
      series: [
        { name: "Number of Events", type: "column", data: overview && overview.eventCounts ? overview.eventCounts : [0] },
        { name: "Registrations", type: "area", data: overview && overview.registrations ? overview.registrations : [0] },
        { name: "Active Events", type: "line", data: overview && overview.activeEvents ? overview.activeEvents : [0] }
      ],
      chart: {
        height: 320,
        type: "line",
        stacked: false,
        toolbar: { show: false }
      },
      stroke: {
        width: [0, 2, 2],
        curve: "straight",
        dashArray: [0, 0, 5]
      },
      plotOptions: {
        bar: {
          columnWidth: "18%",
          borderRadius: 6
        }
      },
      fill: {
        type: "gradient",
        gradient: {
          inverseColors: false,
          shade: "light",
          type: "vertical",
          colorStops: [
            [
              { offset: 0, color: "var(--primary)", opacity: 1 },
              { offset: 100, color: "var(--primary)", opacity: 1 }
            ],
            [
              { offset: 0, color: "#3AC977", opacity: 1 },
              { offset: 45, color: "#3AC977", opacity: 0.18 },
              { offset: 100, color: "#3AC977", opacity: 0 }
            ],
            [
              { offset: 0, color: "#FF5E5E", opacity: 1 },
              { offset: 100, color: "#FF5E5E", opacity: 1 }
            ]
          ]
        }
      },
      colors: ["var(--primary)", "#3AC977", "#FF5E5E"],
      labels: overview && overview.labels ? overview.labels : ["No data"],
      markers: { size: 0 },
      legend: {
        fontSize: "13px",
        fontFamily: "Poppins",
        labels: { colors: "#888888" }
      },
      xaxis: {
        labels: {
          style: {
            fontSize: "13px",
            colors: "#888888",
            fontFamily: "Poppins"
          }
        }
      },
      yaxis: {
        min: 0,
        tickAmount: 4,
        labels: {
          style: {
            fontSize: "13px",
            colors: "#888888",
            fontFamily: "Poppins"
          }
        }
      },
      tooltip: {
        shared: true,
        intersect: false
      }
    };

    if (eventOverviewChart) {
      eventOverviewChart.destroy();
    }
    eventOverviewChart = new ApexCharts(chartEl, options);
    eventOverviewChart.render();
  }

  function renderOverviewStats(events) {
    if (!overviewStats) return;

    var totalRegistrations = events.reduce(function (sum, ev) {
      return sum + Number(ev && ev.inscriptionsCount ? ev.inscriptionsCount : 0);
    }, 0);
    var liveEvents = events.filter(function (ev) {
      return !isEventTerminated(ev);
    }).length;
    var totalCapacity = events.reduce(function (sum, ev) {
      return sum + Number(ev && ev.capacite ? ev.capacite : 0);
    }, 0);
    var pendingCount = Array.isArray(pendingInscriptionsCache) ? pendingInscriptionsCache.length : 0;
    var waitlistMoves = Array.isArray(adminNotificationsCache) ? adminNotificationsCache.length : 0;
    var fillRate = totalCapacity > 0 ? Math.round((totalRegistrations / totalCapacity) * 100) : 0;

	    overviewStats.innerHTML = [
	      { label: "Total Events", value: events.length, accent: liveEvents + " live", icon: "calendar", tone: "event-data" },
	      { label: "Live Schedule", value: liveEvents, accent: "active", icon: "pulse", tone: "std-data" },
	      { label: "Seat Occupancy", value: fillRate + "%", accent: totalRegistrations + " regs", icon: "chart", tone: "teach-data" },
	      { label: "Pending Requests", value: pendingCount, accent: pendingCount ? "needs review" : "clear", icon: "inbox", tone: "food-data" },
	      { label: "Waitlist Moves", value: waitlistMoves, accent: waitlistMoves ? "spot open" : "clear", icon: "shuffle", tone: "std-data" }
	    ].map(function (item) {
	      return (
	        '<article class="yb-overview-stat">' +
	        '<div class="content-box">' +
	        '<div class="chart-num yb-overview-chart-num">' +
	        '<h2 class="font-w700 mb-0 yb-overview-stat-value">' + escapeHtml(item.value) + '</h2>' +
	        '<p class="yb-overview-stat-label">' + escapeHtml(item.label) + '</p>' +
	        '<span class="yb-overview-stat-meta">' + escapeHtml(item.accent) + '</span>' +
	        '</div>' +
	        '<div class="icon-box icon-box-xl ' + item.tone + ' yb-overview-stat-icon">' + getOverviewIconSvg(item.icon) + '</div>' +
	        '</div>' +
	        '</article>'
	      );
	    }).join("");
  }

  function getOverviewIconSvg(icon) {
    var icons = {
      calendar:
        '<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M8 2v4"></path><path d="M16 2v4"></path><rect x="3" y="4.5" width="18" height="16.5" rx="3"></rect><path d="M3 9.5h18"></path><path d="M8 13h3"></path><path d="M13 13h3"></path><path d="M8 17h3"></path>' +
        '</svg>',
      pulse:
        '<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M3 12h4l2.2-4.2L13 16l2.1-4H21"></path><path d="M4 6.5h16"></path><path d="M4 17.5h16"></path>' +
        '</svg>',
      chart:
        '<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M4 19h16"></path><path d="M7 16V9"></path><path d="M12 16V5"></path><path d="M17 16v-4"></path><path d="M5 16.5 9.5 12l3 2.5L19 8"></path>' +
        '</svg>',
      inbox:
        '<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M4 13.5V7a3 3 0 0 1 3-3h10a3 3 0 0 1 3 3v6.5"></path><path d="M4 13.5h4l2 3h4l2-3h4"></path><path d="M5 13.5V17a3 3 0 0 0 3 3h8a3 3 0 0 0 3-3v-3.5"></path>' +
        '</svg>',
      shuffle:
        '<svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="1.9" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' +
        '<path d="M16 3h5v5"></path><path d="M4 7h5l7 10h5"></path><path d="M21 16v5h-5"></path><path d="M16 21l-2.8-4"></path><path d="M4 17h5l2.2-3"></path>' +
        '</svg>'
    };

    return icons[icon] || icons.calendar;
  }

  document.addEventListener("click", function (event) {
    var scheduleButton = event.target.closest("[data-scroll-calendar='true']");
    if (!scheduleButton || !calendarCard) return;
    calendarCard.scrollIntoView({ behavior: "smooth", block: "start" });
  });

  window.addEventListener("resize", function () {
    renderEventCards(getFilteredAndSortedEvents());
    setTimeout(function () {
      calendar.updateSize();
    }, 80);
  });
});
