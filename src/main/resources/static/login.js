(() => {
  const form = document.querySelector("#login-form");
  const email = document.querySelector("#email");
  const password = document.querySelector("#password");
  const status = document.querySelector("#form-status");
  const avatarStage = document.querySelector(".avatar-stage");
  const passwordToggle = document.querySelector(".password-toggle");

  if (!form || !email || !password || !status || !avatarStage) return;

  const setStatus = (message, type = "") => {
    status.textContent = message;
    status.className = `form-status${type ? ` is-${type}` : ""}`;
  };

  const setFieldError = (field, message) => {
    const error = document.querySelector(`[data-error-for="${field.id}"]`);
    field.toggleAttribute("aria-invalid", Boolean(message));
    if (error) error.textContent = message;
  };

  const validate = () => {
    let valid = true;
    const emailValue = email.value.trim();
    const passwordValue = password.value;

    if (!emailValue) {
      setFieldError(email, "Vui lòng nhập email.");
      valid = false;
    } else if (!email.validity.valid) {
      setFieldError(email, "Email chưa đúng định dạng.");
      valid = false;
    } else {
      setFieldError(email, "");
    }

    if (!passwordValue) {
      setFieldError(password, "Vui lòng nhập mật khẩu.");
      valid = false;
    } else if (passwordValue.length < 8) {
      setFieldError(password, "Mật khẩu cần ít nhất 8 ký tự.");
      valid = false;
    } else {
      setFieldError(password, "");
    }

    return valid;
  };

  password.addEventListener("focus", () => avatarStage.dataset.state = "password");
  password.addEventListener("blur", () => {
    if (document.activeElement !== password) avatarStage.dataset.state = "idle";
  });

  email.addEventListener("focus", () => avatarStage.dataset.state = "email");
  email.addEventListener("blur", () => {
    if (document.activeElement !== email) avatarStage.dataset.state = "idle";
  });

  document.addEventListener("pointermove", (event) => {
    if (avatarStage.dataset.state === "password") return;
    const bounds = avatarStage.getBoundingClientRect();
    const x = Math.max(-1, Math.min(1, (event.clientX - (bounds.left + bounds.width / 2)) / (bounds.width * 1.5)));
    const y = Math.max(-1, Math.min(1, (event.clientY - (bounds.top + bounds.height / 2)) / (bounds.height * 1.5)));
    avatarStage.style.setProperty("--eye-x", `${(x * 4).toFixed(2)}px`);
    avatarStage.style.setProperty("--eye-y", `${(y * 3).toFixed(2)}px`);
  });

  passwordToggle.addEventListener("click", () => {
    const visible = password.type === "text";
    password.type = visible ? "password" : "text";
    passwordToggle.setAttribute("aria-pressed", String(!visible));
    passwordToggle.setAttribute("aria-label", visible ? "Hiện mật khẩu" : "Ẩn mật khẩu");
    password.focus();
  });

  form.addEventListener("submit", (event) => {
    event.preventDefault();
    setStatus("");
    if (!validate()) {
      setStatus("Vui lòng kiểm tra lại thông tin.", "error");
      return;
    }

    // Backend này là OAuth2 Resource Server; Identity Server xử lý đăng nhập thật.
    // Khi frontend auth flow được cấu hình, thay callback này bằng PKCE authorize flow.
    setStatus("Giao diện đã sẵn sàng cho luồng xác thực Identity Server.", "success");
  });

  [email, password].forEach((field) => {
    field.addEventListener("input", () => {
      if (field.hasAttribute("aria-invalid")) validate();
      if (status.classList.contains("is-error")) setStatus("");
    });
  });

  document.querySelector("#forgot-link")?.addEventListener("click", (event) => {
    event.preventDefault();
    setStatus("Liên kết khôi phục sẽ được nối với Identity Server.");
  });
})();
