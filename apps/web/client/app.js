const API_URL = window.localStorage.getItem("catCareApiUrl") || "http://127.0.0.1:8000";

const elements = {
  catName: document.querySelector("#cat-name"),
  statusCard: document.querySelector("#status-card"),
  statusLabel: document.querySelector("#status-label"),
  statusCopy: document.querySelector("#status-copy"),
  responsibilities: document.querySelector("#responsibilities"),
  timeline: document.querySelector("#timeline"),
  error: document.querySelector("#error"),
  form: document.querySelector("#responsibility-form"),
  showForm: document.querySelector("#show-form"),
  cancelForm: document.querySelector("#cancel-form"),
};

const labels = {
  clear: "All calm",
  planned: "Care planned",
  due_soon: "Coming up",
  overdue: "Needs attention",
  unknown: "Needs scheduling",
};

async function request(path, options = {}) {
  const response = await fetch(`${API_URL}${path}`, {
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    throw new Error(body.detail || `Request failed (${response.status})`);
  }
  return response.json();
}

function showError(error) {
  elements.error.textContent = `${error.message}. Is the local API running on port 8000?`;
  elements.error.hidden = false;
}

function clearError() {
  elements.error.hidden = true;
  elements.error.textContent = "";
}

function formatDate(value) {
  if (!value) return "No due date yet";
  return new Intl.DateTimeFormat(undefined, { dateStyle: "medium", timeStyle: "short" }).format(new Date(value));
}

function renderResponsibilities(items) {
  elements.responsibilities.replaceChildren();
  if (!items.length) {
    const empty = document.createElement("p");
    empty.className = "empty";
    empty.textContent = "No responsibilities yet. Add one when something needs care.";
    elements.responsibilities.append(empty);
    return;
  }

  for (const item of items) {
    const card = document.createElement("article");
    card.className = `responsibility ${item.state}`;
    card.dataset.responsibilityId = item.id;
    const copy = document.createElement("div");
    const title = document.createElement("h3");
    title.textContent = item.title;
    const metadata = document.createElement("p");
    metadata.textContent = `${item.category} · ${formatDate(item.due_at)} · ${item.derived_state.replace("_", " ")}`;
    copy.append(title, metadata);
    card.append(copy);

    if (item.state === "planned") {
      const complete = document.createElement("button");
      complete.className = "complete-button";
      complete.type = "button";
      complete.textContent = "Mark complete";
      complete.setAttribute("aria-label", `Mark ${item.title} complete`);
      complete.addEventListener("click", () => completeResponsibility(item.id, complete));
      card.append(complete);
    }
    elements.responsibilities.append(card);
  }
}

function renderTimeline(items) {
  elements.timeline.replaceChildren();
  if (!items.length) {
    const empty = document.createElement("li");
    empty.className = "empty";
    empty.textContent = "Care history will appear here.";
    elements.timeline.append(empty);
    return;
  }
  for (const item of items.slice(0, 8)) {
    const row = document.createElement("li");
    const title = document.createElement("strong");
    title.textContent = item.type === "responsibility_completed" ? `Completed ${item.description}` : `Added ${item.description}`;
    const time = document.createElement("time");
    time.dateTime = item.occurred_at;
    time.textContent = formatDate(item.occurred_at);
    row.append(title, time);
    elements.timeline.append(row);
  }
}

async function refresh() {
  clearError();
  try {
    const [cat, status, responsibilities, timeline] = await Promise.all([
      request("/api/v1/cat"),
      request("/api/v1/status"),
      request("/api/v1/responsibilities"),
      request("/api/v1/timeline"),
    ]);
    elements.catName.textContent = cat.name;
    elements.statusCard.dataset.kind = status.kind;
    elements.statusLabel.textContent = labels[status.kind] || "Care status";
    elements.statusCopy.textContent = status.sentence;
    renderResponsibilities(responsibilities);
    renderTimeline(timeline);
  } catch (error) {
    showError(error);
  }
}

async function completeResponsibility(id, button) {
  button.disabled = true;
  try {
    await request(`/api/v1/responsibilities/${id}/complete`, { method: "POST" });
    await refresh();
  } catch (error) {
    button.disabled = false;
    showError(error);
  }
}

elements.showForm.addEventListener("click", () => {
  elements.form.hidden = false;
  elements.showForm.hidden = true;
  elements.form.elements.title.focus();
});

elements.cancelForm.addEventListener("click", () => {
  elements.form.reset();
  elements.form.hidden = true;
  elements.showForm.hidden = false;
});

elements.form.addEventListener("submit", async (event) => {
  event.preventDefault();
  const data = new FormData(elements.form);
  const localDueAt = data.get("due_at");
  const command = {
    title: data.get("title"),
    category: data.get("category"),
    due_at: localDueAt ? new Date(localDueAt).toISOString() : null,
  };
  const submit = elements.form.querySelector("button[type=submit]");
  submit.disabled = true;
  try {
    await request("/api/v1/responsibilities", {
      method: "POST",
      body: JSON.stringify(command),
    });
    elements.form.reset();
    elements.form.hidden = true;
    elements.showForm.hidden = false;
    await refresh();
  } catch (error) {
    showError(error);
  } finally {
    submit.disabled = false;
  }
});

refresh();
