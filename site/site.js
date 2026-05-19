/* Ammonite Docs — site.js */
(function () {
  "use strict";

  const NAV = [
    { slug: "commands",     title: "Commands",          href: "commands" },
    { slug: "features",     title: "Features",          href: "features" },
    { slug: "setup",        title: "Setup Guide",       href: "setup" },
    { slug: "permissions",  title: "Permissions",       href: "permissions" },
    { slug: "embedbuilder", title: "Embed Builder",     href: "embedbuilder" },
    { slug: "privacy",      title: "Privacy Policy",    href: "privacy" },
    { slug: "terms",        title: "Terms of Service",  href: "terms" },
    { slug: "support",      title: "Support & FAQ",     href: "support" },
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

  function initSearch() {
    const header = document.querySelector(".site-header");
    if (!header) return;

    const wrapper = document.createElement("div");
    wrapper.className = "site-search";

    const input = document.createElement("input");
    input.type = "search";
    input.className = "site-search-input";
    input.placeholder = "Search docs…";
    input.setAttribute("aria-label", "Search documentation");
    input.setAttribute("autocomplete", "off");

    const dropdown = document.createElement("div");
    dropdown.className = "site-search-dropdown";

    wrapper.appendChild(input);
    wrapper.appendChild(dropdown);
    header.appendChild(wrapper);

    var index = null;
    var currentResults = [];
    var focusedIdx = -1;
    var suppressDropdown = false;

    // Determine path prefix to reach site root from current page
    var depth = (window.location.pathname.match(/\//g) || []).length - 1;
    // For ammonitedocs all pages are at the root so prefix is empty
    var prefix = "";

    fetch(prefix + "search-index.json")
      .then(function(r) { return r.json(); })
      .then(function(data) { index = data; })
      .catch(function() { index = []; });

    function query(q) {
      if (!index) return [];
      var terms = q.toLowerCase().split(/\s+/).filter(Boolean);
      var scored = [];
      for (var i = 0; i < index.length; i++) {
        var item = index[i];
        var haystack = (item.title + " " + item.section + " " + item.snippet).toLowerCase();
        var score = 0;
        for (var t = 0; t < terms.length; t++) {
          var term = terms[t];
          if (item.title.toLowerCase().includes(term)) score += 10;
          if (item.section.toLowerCase().includes(term)) score += 3;
          if (item.snippet.toLowerCase().includes(term)) score += 1;
        }
        if (score > 0) scored.push({ item: item, score: score });
      }
      scored.sort(function(a, b) { return b.score - a.score; });
      return scored.slice(0, 8).map(function(s) { return s.item; });
    }

    function esc(s) { return s.replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;"); }

    function highlight(text, terms) {
      var out = esc(text);
      terms.forEach(function(t) {
        if (!t) return;
        out = out.replace(new RegExp("(" + t.replace(/[.*+?^${}()|[\]\\]/g,"\\$&") + ")", "gi"),
          '<mark style="background:var(--brand-primary);color:#000;border-radius:2px;padding:0 2px">$1</mark>');
      });
      return out;
    }

    function renderResults(results, terms) {
      currentResults = results;
      focusedIdx = -1;
      dropdown.innerHTML = "";
      if (results.length === 0) {
        var empty = document.createElement("div");
        empty.className = "search-empty";
        empty.textContent = "No results found";
        dropdown.appendChild(empty);
      } else {
        results.forEach(function(item) {
          var a = document.createElement("a");
          a.className = "search-result";
          a.href = item.href;
          var sectionHtml = item.section ? '<span class="search-result-section">' + esc(item.section) + '</span>' : '';
          var snippetHtml = item.snippet ? '<span class="search-result-snippet">' + highlight(item.snippet.slice(0, 120), terms) + '</span>' : '';
          a.innerHTML = '<span class="search-result-title">' + highlight(item.title, terms) + '</span>' + sectionHtml + snippetHtml;
          a.addEventListener("mousedown", function(e) {
            e.preventDefault();
            window.location.href = item.href;
          });
          dropdown.appendChild(a);
        });
      }
      dropdown.classList.add("open");
    }

    function hideDropdown() {
      dropdown.classList.remove("open");
      focusedIdx = -1;
      dropdown.querySelectorAll(".search-result").forEach(function(el) {
        el.classList.remove("focused");
      });
    }

    function updateFocus() {
      var els = dropdown.querySelectorAll(".search-result");
      els.forEach(function(el, i) { el.classList.toggle("focused", i === focusedIdx); });
      if (focusedIdx >= 0 && els[focusedIdx]) els[focusedIdx].scrollIntoView({ block: "nearest" });
    }

    function runSearch() {
      var q = input.value.trim();
      if (!q) { hideDropdown(); return; }
      var terms = q.toLowerCase().split(/\s+/).filter(Boolean);
      var results = query(q);
      renderResults(results, terms);
    }

    input.addEventListener("input", function() { suppressDropdown = false; runSearch(); });
    input.addEventListener("focus", function() { if (!suppressDropdown && input.value.trim()) runSearch(); });
    input.addEventListener("blur", function() { setTimeout(hideDropdown, 150); });

    input.addEventListener("keydown", function(e) {
      var els = dropdown.querySelectorAll(".search-result");
      if (e.key === "Escape") {
        suppressDropdown = true; hideDropdown(); input.blur(); return;
      }
      if (e.key === "ArrowDown") {
        e.preventDefault(); suppressDropdown = false;
        if (!dropdown.classList.contains("open") && input.value.trim()) runSearch();
        if (els.length) { focusedIdx = Math.min(focusedIdx + 1, els.length - 1); updateFocus(); }
        return;
      }
      if (e.key === "ArrowUp") {
        e.preventDefault();
        if (els.length) { focusedIdx = Math.max(focusedIdx - 1, 0); updateFocus(); }
        return;
      }
      if (e.key === "Enter") {
        e.preventDefault();
        var target = focusedIdx >= 0 ? currentResults[focusedIdx] : currentResults[0];
        if (target) window.location.href = target.href;
        return;
      }
    });

    document.addEventListener("click", function(e) { if (!wrapper.contains(e.target)) hideDropdown(); });
  }

  window.AmDoc = {
    init: function (activeSlug) {
      buildSidebar(activeSlug);
      buildPageNav(activeSlug);
      initMobileToggle();
      initSearch();
    }
  };
})();
