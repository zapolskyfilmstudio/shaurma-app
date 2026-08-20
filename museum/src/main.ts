const year = document.querySelector<HTMLElement>("[data-year]");
if (year) {
  year.textContent = String(new Date().getFullYear());
}
