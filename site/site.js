/* Ammonite Docs — site.js */
(function () {
  "use strict";

  const NAV = [
    { slug: "commands",    title: "Commands",         href: "commands.html" },
    { slug: "features",    title: "Features",         href: "features.html" },
    { slug: "setup",       title: "Setup Guide",      href: "setup.html" },
    { slug: "permissions", title: "Permissions",      href: "permissions.html" },
    { slug: "privacy",     title: "Privacy Policy",   href: "privacy.html" },
    { slug: "terms",       title: "Terms of Service", href: "terms.html" },
    { slug: "support",     title: "Support & FAQ",    href: "support.html" },
  ];

  function buildSidebar(activeSlug) {
    const aside = document.querySelector(".sidebar");
    if (!aside) return;

    const title = document.createElement("div");
    title.className = "sidebar-group-title";
    title.textContent = "Documentation";
    aside.appendChild(title);

    NAV.forEach(function (page) {
      const a = document.createElement("a");
      a.href = page.href;
      a.textContent = page.title;
      if (page.slug === activeSlug) a.classList.add("active");
      aside.appendChild(a);
    });
  }

  function buildPageNav(activeSlug) {
    const nav = document.querySelector(".page-nav");
    if (!nav) return;
    const idx = NAV.findIndex(function (p) { return p.slug === activeSlug; });
    if (idx === -1) return;
    const prev = NAV[idx - 1];
    const next = NAV[idx + 1];
    if (prev) {
      const a = document.createElement("a");
      a.className = "btn-nav prev";
      a.href = prev.href;
      a.innerHTML = "&#8592; " + prev.title;
      nav.appendChild(a);
    }
    if (next) {
      const a = document.createElement("a");
      a.className = "btn-nav next";
      a.href = next.href;
      a.innerHTML = next.title + " &#8594;";
      nav.appendChild(a);
    }
  }

  function initMobileToggle() {
    const toggle = document.querySelector(".sidebar-toggle");
    const sidebar = document.querySelector(".sidebar");
    const backdrop = document.querySelector(".sidebar-backdrop");
    if (!toggle || !sidebar) return;
    function close() {
      sidebar.classList.remove("open");
      if (backdrop) backdrop.classList.remove("open");
    }
    toggle.addEventListener("click", function () {
      const open = sidebar.classList.toggle("open");
      if (backdrop) backdrop.classList.toggle("open", open);
    });
    if (backdrop) backdrop.addEventListener("click", close);
    document.addEventListener("keydown", function (e) {
      if (e.key === "Escape") close();
    });
  }

  window.AmDoc = {
    init: function (activeSlug) {
      buildSidebar(activeSlug);
      buildPageNav(activeSlug);
      initMobileToggle();
    }
  };
})();
