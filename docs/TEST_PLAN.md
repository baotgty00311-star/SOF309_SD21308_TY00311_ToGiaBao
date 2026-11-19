# KẾ HOẠCH KIỂM THỬ (TEST PLAN)

* **Dự án:** Website Đặt lịch & Quản lý Chăm sóc Thú cưng **PetCare** (`sof309-assignment`)
* **Sinh viên:** Tô Gia Bảo — MSSV: `TY00311` — Lớp: `SD21308`

---

## 1. MỤC TIÊU KIỂM THỬ
* Kiểm thử logic nghiệp vụ backend (Unit Test với **JUnit 5** cho Controller / Service / Model / ValidationUtil), áp dụng `@ParameterizedTest`, `@CsvSource`, `assertThrows()`, và `@Timeout`.
* Đảm bảo các bài Unit Test chạy độc lập, không phụ thuộc kết nối SQL Server trực tiếp thông qua mô hình Dependency Injection và Fake DAO in-memory.
* Kiểm thử giao diện tự động (Automation UI Test với **Selenium WebDriver 4** theo mô hình Page Object Model).
* Tích hợp kiểm thử tự động với **GitHub Actions CI/CD** (`.github/workflows/test.yml`).

## 2. PHẠM VI KIỂM THỬ
* **In-Scope (Unit Test - Y3):**
  * `AuthService` & `ValidationUtil` (`AuthControllerTest`): chuẩn hóa/validate SĐT & Email, đăng ký khách hàng (bao gồm biên mật khẩu 8 ký tự, tự sinh email khi trống, trùng SĐT/Email), đăng nhập, cập nhật hồ sơ, đổi mật khẩu, hash password.
  * `BookingService`, `HomeServlet` & `AppointmentBalance` (`BookingControllerTest`): đặt lịch thành công và tính `scheduledEnd`, ngoại lệ thú cưng không tồn tại/không thuộc chủ sở hữu, dịch vụ không tồn tại/ngừng hoạt động, ánh xạ mã lỗi SQL Server (`51001..51032`), dữ liệu mặc định trang chủ và tính số tiền còn lại.
  * `SampleControllerTest`: test mẫu kiểm tra luồng xác thực cơ bản.
* **In-Scope (UI Automation - Y4):**
  * Luồng Đăng nhập (`LoginPage`, `LoginTest`) và các luồng tương tác trình duyệt tiếp theo.
* **Out-of-Scope:**
  * Kiểm thử hiệu năng tải cao (Load/Stress Test), kiểm thử xâm nhập bảo mật chuyên sâu, chạy integration test trực tiếp trên SQL Server thật trong CI.

## 3. TIÊU CHÍ HOÀN THÀNH
* Toàn bộ Unit Test (`SampleControllerTest`, `AuthControllerTest`, `BookingControllerTest`) chạy qua `mvn test` đạt **100% Pass** (0 Failure, 0 Error).
* Các file nhật ký minh chứng sử dụng AI (`docs/ai-logs/`) ghi chép đầy đủ Prompt, Raw Output và phần chỉnh sửa của sinh viên.
* Pipeline GitHub Actions báo xanh (Success) khi đẩy code lên nhánh `main`.
