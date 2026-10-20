// ParkEasy - Shared JavaScript
// Handles authentication, navigation and API requests.

const API_BASE = "http://localhost:8080";

/* =========================
   SESSION
========================= */

function getToken() {
  return localStorage.getItem("token");
}

function getUsername() {
  return localStorage.getItem("username");
}

function isLoggedIn() {
  return !!getToken();
}

function saveSession(token, username) {
  localStorage.setItem("token", token);
  localStorage.setItem("username", username);
}


/* =========================
   LOGOUT
========================= */

async function logout() {

  try {
    await fetch(API_BASE + "/api/logout", {
      method: "POST",
      headers: {
        "Authorization": "Bearer " + getToken()
      }
    });
  } catch (e) {
    // Continue logout even if server is unavailable
  }

  localStorage.removeItem("token");
  localStorage.removeItem("username");

  location.href = "login.html";
}


/* =========================
   AUTHENTICATION
========================= */

function requireAuth() {

  if (!isLoggedIn()) {
    location.href = "login.html";
  }
}


/* =========================
   NAVIGATION
========================= */

function renderNav(activePage) {

  const authArea = document.getElementById("authArea");

  if (authArea) {

    if (isLoggedIn()) {

      authArea.innerHTML = `
        <span class="user-chip">
          👤 ${escapeHtml(getUsername())}
        </span>
        <a href="#" id="logoutLink">Logout</a>
      `;

      const logoutLink =
        document.getElementById("logoutLink");

      logoutLink.onclick = function (event) {
        event.preventDefault();
        logout();
      };

    } else {

      authArea.innerHTML = `
        <a href="login.html">Login</a>
        <a href="register.html">Register</a>
      `;
    }
  }

  // Highlight current page
  document
    .querySelectorAll(".nav a[data-page]")
    .forEach(link => {

      if (link.dataset.page === activePage) {
        link.classList.add("active");
      }

    });
}


/* =========================
   SECURITY
========================= */

function escapeHtml(value) {

  return String(value ?? "").replace(
    /[&<>"']/g,
    character => ({
      "&": "&amp;",
      "<": "&lt;",
      ">": "&gt;",
      '"': "&quot;",
      "'": "&#39;"
    }[character])
  );
}


/* =========================
   API POST
========================= */

async function apiPost(path, data) {

  try {

    const headers = {
      "Content-Type":
        "application/x-www-form-urlencoded"
    };

    const token = getToken();

    if (token) {
      headers["Authorization"] =
        "Bearer " + token;
    }

    const response = await fetch(
      API_BASE + path,
      {
        method: "POST",
        headers: headers,
        body: new URLSearchParams(data)
      }
    );

    const responseData =
      await response.json().catch(() => ({}));

    return {
      ok: response.ok,
      status: response.status,
      data: responseData
    };

  } catch (error) {

    return {
      ok: false,
      status: 0,
      data: {
        error: "Unable to connect to the server."
      }
    };
  }
}


/* =========================
   API GET
========================= */

async function apiGet(path) {

  try {

    const headers = {};

    const token = getToken();

    if (token) {
      headers["Authorization"] =
        "Bearer " + token;
    }

    const response =
      await fetch(API_BASE + path, {
        headers: headers
      });

    const responseData =
      await response.json().catch(() => ({}));

    return {
      ok: response.ok,
      status: response.status,
      data: responseData
    };

  } catch (error) {

    return {
      ok: false,
      status: 0,
      data: {
        error: "Unable to connect to the server."
      }
    };
  }
}