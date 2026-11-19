package com.poly.sof309.controllers;

import com.petcare.controllers.DatLichServlet;
import com.petcare.controllers.HomeServlet;
import com.petcare.daos.AppointmentDAO;
import com.petcare.daos.PetDAO;
import com.petcare.daos.ServiceDAO;
import com.petcare.daos.StaffDAO;
import com.petcare.model.ReviewItem;
import com.petcare.model.ServiceCard;
import com.petcare.models.AppointmentBalance;
import com.petcare.models.Pets;
import com.petcare.models.Services;
import com.petcare.models.StaffOption;
import com.petcare.models.Users;
import com.petcare.services.BookingService;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * =========================================================================
 * [Y3] UNIT TEST CHO NGHIỆP VỤ ĐẶT LỊCH & TRANG CHỦ (BookingService, HomeServlet, AppointmentBalance)
 * Áp dụng @ParameterizedTest, @CsvSource, assertThrows() và @Timeout.
 * =========================================================================
 */
public class BookingControllerTest {

    private FakePetDAO fakePetDAO;
    private FakeServiceDAO fakeServiceDAO;
    private FakeStaffDAO fakeStaffDAO;
    private FakeAppointmentDAO fakeAppointmentDAO;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        fakePetDAO = new FakePetDAO();
        fakeServiceDAO = new FakeServiceDAO();
        fakeStaffDAO = new FakeStaffDAO();
        fakeAppointmentDAO = new FakeAppointmentDAO();

        // Seed pets: pet 1 thuộc customer 10, pet 2 thuộc customer 20
        Pets pet1 = new Pets();
        pet1.setPetId(1);
        pet1.setOwnerId(10);
        pet1.setName("Bé Miu");
        pet1.setSpecies("Mèo");
        pet1.setBreed("Mèo Anh lông ngắn");
        fakePetDAO.seed(pet1);

        Pets pet2 = new Pets();
        pet2.setPetId(2);
        pet2.setOwnerId(20);
        pet2.setName("Bé Bơ");
        pet2.setSpecies("Chó");
        pet2.setBreed("Corgi");
        fakePetDAO.seed(pet2);

        // Seed services: service 1 hoạt động (45 phút), service 2 đã ngừng hoạt động
        Services activeService = new Services();
        activeService.setServiceId(1);
        activeService.setName("Tắm sấy thảo mộc");
        activeService.setCategory("GROOMING");
        activeService.setDurationMinutes(45);
        activeService.setPrice(new BigDecimal("150000"));
        activeService.setActive(true);
        fakeServiceDAO.seed(activeService);

        Services inactiveService = new Services();
        inactiveService.setServiceId(2);
        inactiveService.setName("Dịch vụ tạm ngưng");
        inactiveService.setCategory("GROOMING");
        inactiveService.setDurationMinutes(60);
        inactiveService.setPrice(new BigDecimal("200000"));
        inactiveService.setActive(false);
        fakeServiceDAO.seed(inactiveService);

        fakeStaffDAO.seed(1, new StaffOption(5, "Trần Minh Khoa"));

        bookingService = new BookingService(fakePetDAO, fakeServiceDAO, fakeAppointmentDAO);
    }

    @Test
    @Timeout(value = 2, unit = TimeUnit.SECONDS)
    @DisplayName("TC_BOOK_01: Đặt lịch thành công tự động cộng thời lượng dịch vụ vào scheduledEnd")
    void testBookServiceSuccess() throws SQLException {
        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 9, 30);

        int appointmentId = bookingService.bookService(10, 1, 1, 5, start);

        assertEquals(101, appointmentId);
        assertEquals(10, fakeAppointmentDAO.lastCustomerId);
        assertEquals(1, fakeAppointmentDAO.lastPetId);
        assertEquals(1, fakeAppointmentDAO.lastServiceId);
        assertEquals(5, fakeAppointmentDAO.lastStaffId);
        assertEquals(Timestamp.valueOf(start), fakeAppointmentDAO.lastScheduledStart);
        assertEquals(Timestamp.valueOf(start.plusMinutes(45)), fakeAppointmentDAO.lastScheduledEnd);
    }

    @Test
    @DisplayName("TC_BOOK_02: Ném IllegalArgumentException khi thú cưng không tồn tại")
    void testBookServicePetNotFoundThrowsException() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 10, 0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookService(10, 999, 1, 5, start));
        assertEquals("Thú cưng không thuộc tài khoản của bạn.", ex.getMessage());
    }

    @Test
    @DisplayName("TC_BOOK_03: Ném IllegalArgumentException khi thú cưng thuộc khách hàng khác")
    void testBookServicePetOwnedByAnotherCustomerThrowsException() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 10, 0);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookService(10, 2, 1, 5, start));
        assertEquals("Thú cưng không thuộc tài khoản của bạn.", ex.getMessage());
    }

    @Test
    @DisplayName("TC_BOOK_04: Ném IllegalArgumentException khi dịch vụ không tồn tại hoặc ngừng hoạt động")
    void testBookServiceInvalidOrInactiveServiceThrowsException() {
        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 10, 0);

        IllegalArgumentException notFoundEx = assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookService(10, 1, 999, 5, start));
        assertEquals("Dịch vụ không tồn tại hoặc đã ngừng hoạt động.", notFoundEx.getMessage());

        IllegalArgumentException inactiveEx = assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookService(10, 1, 2, 5, start));
        assertEquals("Dịch vụ không tồn tại hoặc đã ngừng hoạt động.", inactiveEx.getMessage());
    }

    @ParameterizedTest(name = "[{index}] SQL error code {0} -> thông báo thân thiện")
    @DisplayName("TC_BOOK_SQL_MAP: Kiểm tra ánh xạ mã lỗi SQL Server sang thông báo tiếng Việt")
    @CsvSource({
        "51001, 'Hệ thống đang xử lý booking khác, vui lòng thử lại.'",
        "51031, 'Thú cưng không thuộc tài khoản của bạn hoặc tài khoản đã bị khóa.'",
        "51032, 'Dịch vụ hoặc nhân viên không hợp lệ.'",
        "51009, 'Khung giờ chọn không nằm trong ca làm việc hoặc giờ mở cửa của cơ sở.'",
        "51010, 'Nhân viên hoặc thú cưng đã có lịch hẹn trùng giờ này.'",
        "51008, 'Tài khoản của bạn hiện không hoạt động.'",
        "99999, 'Không thể đặt lịch. Vui lòng kiểm tra lại thông tin và thử lại.'"
    })
    void testBookServiceSqlErrorCodeTranslation(int sqlErrorCode, String expectedMessage) {
        fakeAppointmentDAO.sqlExceptionToThrow = new SQLException("Raw SQL error", "S0001", sqlErrorCode);
        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 14, 0);

        SQLException ex = assertThrows(SQLException.class,
                () -> bookingService.bookService(10, 1, 1, 5, start));
        assertEquals(expectedMessage, ex.getMessage());
        assertNotNull(ex.getCause());
    }

    @Test
    @DisplayName("TC_HOME_01: HomeServlet.buildDefaultServices trả về 6 dịch vụ hợp lệ")
    void testHomeServletDefaultServices() {
        List<ServiceCard> services = HomeServlet.buildDefaultServices();

        assertEquals(6, services.size());
        for (ServiceCard card : services) {
            assertNotNull(card.getTitle());
            assertFalse(card.getTitle().isBlank());
            assertNotNull(card.getPriceValue());
            assertFalse(card.getPriceValue().isBlank());
        }
    }

    @Test
    @DisplayName("TC_HOME_02: HomeServlet.buildDefaultReviews trả về 3 đánh giá khách hàng")
    void testHomeServletDefaultReviews() {
        List<ReviewItem> reviews = HomeServlet.buildDefaultReviews();

        assertEquals(3, reviews.size());
        for (ReviewItem item : reviews) {
            assertNotNull(item.getAuthorName());
            assertFalse(item.getAuthorName().isBlank());
            assertNotNull(item.getQuote());
            assertFalse(item.getQuote().isBlank());
        }
    }

    @Test
    @DisplayName("TC_PAY_01: AppointmentBalance.getRemainingAmount tính đúng số tiền còn lại")
    void testAppointmentBalanceRemainingAmount() {
        AppointmentBalance balance = new AppointmentBalance(
                1001,
                "SERVICE",
                "CONFIRMED",
                new BigDecimal("350000"),
                new BigDecimal("100000"),
                Timestamp.valueOf(LocalDateTime.of(2026, 11, 18, 10, 0))
        );

        assertEquals(0, new BigDecimal("250000").compareTo(balance.getRemainingAmount()));
    }

    @ParameterizedTest(name = "[{index}] Thời lượng {0} phút -> cộng đúng vào scheduledEnd")
    @DisplayName("TC_BOOK_DURATION_PARAM: BookingService cộng chính xác thời lượng dịch vụ vào scheduledEnd")
    @CsvSource({
        "30",
        "45",
        "60",
        "90",
        "120"
    })
    void testBookServiceDurationCalculation(int durationMinutes) throws SQLException {
        Services customService = new Services();
        customService.setServiceId(10);
        customService.setName("Gói " + durationMinutes + " phút");
        customService.setCategory("GROOMING");
        customService.setDurationMinutes(durationMinutes);
        customService.setPrice(new BigDecimal("200000"));
        customService.setActive(true);
        fakeServiceDAO.seed(customService);

        LocalDateTime start = LocalDateTime.of(2026, 12, 1, 8, 30);
        bookingService.bookService(10, 1, 10, 5, start);

        assertEquals(Timestamp.valueOf(start.plusMinutes(durationMinutes)),
                fakeAppointmentDAO.lastScheduledEnd);
    }

    @Test
    @DisplayName("TC_BOOK_PET_NULL_OWNER: Thú cưng có ownerId = null bị từ chối khi đặt lịch")
    void testBookServicePetWithNullOwnerThrowsException() {
        Pets orphanPet = new Pets();
        orphanPet.setPetId(3);
        orphanPet.setOwnerId(null);
        orphanPet.setName("Bé Vô Chủ");
        orphanPet.setSpecies("Chó");
        fakePetDAO.seed(orphanPet);

        LocalDateTime start = LocalDateTime.of(2026, 11, 20, 10, 0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> bookingService.bookService(10, 3, 1, 5, start));
        assertEquals("Thú cưng không thuộc tài khoản của bạn.", ex.getMessage());
    }

    @Test
    @DisplayName("TC_DATLICH_SERVLET_AUTH: DatLichServlet chặn khách chưa đăng nhập (/login) và sai vai trò (/home)")
    void testDatLichServletAuthorizationGuards() throws ServletException, IOException {
        DatLichServlet servlet = new DatLichServlet(fakePetDAO, fakeServiceDAO, fakeStaffDAO, bookingService);

        MockServletHelper.MockExchange guestExchange = new MockServletHelper.MockExchange()
                .withContextPath("/sof309-assignment");
        servlet.doGet(guestExchange.request(), guestExchange.response());
        assertEquals("/sof309-assignment/login", guestExchange.getRedirectedUrl());

        Users staffUser = createUser(99, Users.ROLE_STAFF);
        MockServletHelper.MockExchange staffExchange = new MockServletHelper.MockExchange()
                .withContextPath("/sof309-assignment")
                .withLoggedInUser(staffUser);
        servlet.doGet(staffExchange.request(), staffExchange.response());
        assertEquals("/sof309-assignment/home", staffExchange.getRedirectedUrl());
    }

    @Test
    @DisplayName("TC_DATLICH_ADD_PET_OK: DatLichServlet thêm thú cưng mới thành công")
    void testDatLichServletAddPetSuccess() throws ServletException, IOException {
        DatLichServlet servlet = new DatLichServlet(fakePetDAO, fakeServiceDAO, fakeStaffDAO, bookingService);
        Users customer = createUser(10, Users.ROLE_CUSTOMER);

        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(customer)
                .withParam("action", "addPet")
                .withParam("petName", "  Bé Kem  ")
                .withParam("petSpecies", "Chó")
                .withParam("petBreed", "Poodle");

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/customer/datLich.jsp", exchange.getForwardedPath());
        assertEquals("Đã thêm thú cưng \"Bé Kem\".", exchange.getRequestAttribute("successMessage"));
        assertEquals(2, fakePetDAO.findByOwner(10).size());
    }

    @ParameterizedTest(name = "[{index}] Thêm thú cưng thiếu dữ liệu: name=''{0}'', species=''{1}''")
    @DisplayName("TC_DATLICH_ADD_PET_VAL: DatLichServlet báo lỗi khi thiếu tên hoặc loài thú cưng")
    @CsvSource({
        "'', 'Chó'",
        "'Bé Bông', ''",
        "'   ', 'Mèo'"
    })
    void testDatLichServletAddPetValidation(String petName, String petSpecies)
            throws ServletException, IOException {
        DatLichServlet servlet = new DatLichServlet(fakePetDAO, fakeServiceDAO, fakeStaffDAO, bookingService);
        Users customer = createUser(10, Users.ROLE_CUSTOMER);

        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(customer)
                .withParam("action", "addPet")
                .withParam("petName", petName)
                .withParam("petSpecies", petSpecies);

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/customer/datLich.jsp", exchange.getForwardedPath());
        assertEquals("Vui lòng nhập tên và loài thú cưng.", exchange.getRequestAttribute("errorMessage"));
    }

    @ParameterizedTest(name = "[{index}] Đặt lịch lỗi: petId=''{0}'', staffChoice=''{1}'', date=''{2}'', time=''{3}''")
    @DisplayName("TC_DATLICH_BOOK_VAL: DatLichServlet kiểm tra validate đầu vào và ngày quá khứ khi đặt lịch")
    @CsvSource({
        "'', '1_5', '2027-12-20', '10:00', 'Vui lòng chọn đầy đủ thú cưng, dịch vụ/nhân viên, ngày và giờ.'",
        "'1', '', '2027-12-20', '10:00', 'Vui lòng chọn đầy đủ thú cưng, dịch vụ/nhân viên, ngày và giờ.'",
        "'1', '1', '2027-12-20', '10:00', 'Lựa chọn dịch vụ/nhân viên không hợp lệ.'",
        "'1', '1_5_9', '2027-12-20', '10:00', 'Lựa chọn dịch vụ/nhân viên không hợp lệ.'",
        "'abc', '1_5', '2027-12-20', '10:00', 'Thông tin đặt lịch không hợp lệ.'",
        "'1', '1_5', '2026-99-99', '10:00', 'Thông tin đặt lịch không hợp lệ.'",
        "'1', '1_5', '2020-01-01', '10:00', 'Thời gian đặt lịch phải ở trong tương lai.'"
    })
    void testDatLichServletBookValidation(String petId, String staffChoice, String bookingDate,
                                          String bookingTime, String expectedError)
            throws ServletException, IOException {
        DatLichServlet servlet = new DatLichServlet(fakePetDAO, fakeServiceDAO, fakeStaffDAO, bookingService);
        Users customer = createUser(10, Users.ROLE_CUSTOMER);

        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(customer)
                .withParam("action", "book")
                .withParam("petId", petId)
                .withParam("staffChoice", staffChoice)
                .withParam("bookingDate", bookingDate)
                .withParam("bookingTime", bookingTime);

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/customer/datLich.jsp", exchange.getForwardedPath());
        assertEquals(expectedError, exchange.getRequestAttribute("errorMessage"));
    }

    @Test
    @DisplayName("TC_DATLICH_BOOK_OK: DatLichServlet đặt lịch thành công cho khung giờ tương lai")
    void testDatLichServletBookSuccess() throws ServletException, IOException {
        DatLichServlet servlet = new DatLichServlet(fakePetDAO, fakeServiceDAO, fakeStaffDAO, bookingService);
        Users customer = createUser(10, Users.ROLE_CUSTOMER);

        MockServletHelper.MockExchange exchange = new MockServletHelper.MockExchange()
                .withLoggedInUser(customer)
                .withParam("action", "book")
                .withParam("petId", "1")
                .withParam("staffChoice", "1_5")
                .withParam("bookingDate", "2027-12-20")
                .withParam("bookingTime", "10:00");

        servlet.doPost(exchange.request(), exchange.response());

        assertEquals("/customer/datLich.jsp", exchange.getForwardedPath());
        assertEquals("Đặt lịch thành công! Mã lịch hẹn của bạn là #101.",
                exchange.getRequestAttribute("successMessage"));
        assertNotNull(exchange.getRequestAttribute("pets"));
        assertNotNull(exchange.getRequestAttribute("staffByService"));
    }

    @ParameterizedTest(name = "[{index}] Tổng={0}, Đã trả={1} -> Còn lại={2}")
    @DisplayName("TC_PAY_PARAM: AppointmentBalance tính đúng số tiền còn lại theo các trạng thái thanh toán")
    @CsvSource({
        "350000, 0, 350000",
        "350000, 100000, 250000",
        "350000, 350000, 0"
    })
    void testAppointmentBalanceRemainingParameterized(String total, String paid, String expectedRemaining) {
        AppointmentBalance balance = new AppointmentBalance(
                1002,
                "SERVICE",
                "CONFIRMED",
                new BigDecimal(total),
                new BigDecimal(paid),
                Timestamp.valueOf(LocalDateTime.of(2026, 11, 18, 10, 0))
        );

        assertEquals(0, new BigDecimal(expectedRemaining).compareTo(balance.getRemainingAmount()));
    }

    private static Users createUser(int userId, String role) {
        return new Users(userId, "User " + userId, "user" + userId + "@petcare.vn", "0912345678",
                "hash", role, true, LocalDateTime.now());
    }

    private static final class FakePetDAO implements PetDAO {
        private final Map<Integer, Pets> pets = new LinkedHashMap<>();
        private int nextId = 100;

        void seed(Pets pet) {
            pets.put(pet.getPetId(), pet);
        }

        @Override
        public List<Pets> findByOwner(int ownerId) {
            List<Pets> result = new ArrayList<>();
            for (Pets p : pets.values()) {
                if (p.getOwnerId() != null && p.getOwnerId() == ownerId) {
                    result.add(p);
                }
            }
            return result;
        }

        @Override
        public Pets findById(int petId) {
            return pets.get(petId);
        }

        @Override
        public int create(Pets pet) {
            int id = nextId++;
            pet.setPetId(id);
            pets.put(id, pet);
            return id;
        }
    }

    private static final class FakeServiceDAO implements ServiceDAO {
        private final Map<Integer, Services> services = new LinkedHashMap<>();

        void seed(Services service) {
            services.put(service.getServiceId(), service);
        }

        @Override
        public List<Services> findActiveBookable() {
            List<Services> result = new ArrayList<>();
            for (Services s : services.values()) {
                if (s.isActive() && !"BOARDING".equalsIgnoreCase(s.getCategory())) {
                    result.add(s);
                }
            }
            return result;
        }

        @Override
        public Services findById(int serviceId) {
            return services.get(serviceId);
        }
    }

    private static final class FakeStaffDAO implements StaffDAO {
        private final Map<Integer, List<StaffOption>> staffByService = new LinkedHashMap<>();

        void seed(int serviceId, StaffOption option) {
            staffByService.computeIfAbsent(serviceId, k -> new ArrayList<>()).add(option);
        }

        @Override
        public List<StaffOption> findActiveStaffForService(int serviceId) {
            return staffByService.getOrDefault(serviceId, List.of());
        }
    }

    private static final class FakeAppointmentDAO implements AppointmentDAO {
        int nextAppointmentId = 101;
        int lastCustomerId;
        int lastPetId;
        int lastServiceId;
        int lastStaffId;
        Timestamp lastScheduledStart;
        Timestamp lastScheduledEnd;
        SQLException sqlExceptionToThrow;

        @Override
        public int bookService(int customerId, int petId, int serviceId, int staffId,
                               Timestamp scheduledStart, Timestamp scheduledEnd) throws SQLException {
            if (sqlExceptionToThrow != null) {
                throw sqlExceptionToThrow;
            }
            this.lastCustomerId = customerId;
            this.lastPetId = petId;
            this.lastServiceId = serviceId;
            this.lastStaffId = staffId;
            this.lastScheduledStart = scheduledStart;
            this.lastScheduledEnd = scheduledEnd;
            return nextAppointmentId++;
        }
    }
}
