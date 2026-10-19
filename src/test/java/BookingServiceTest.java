package com.ctu.service;

import com.ctu.exception.PaymentFailedException;
import com.ctu.exception.RoomNotAvailableException;
import com.ctu.model.BookingReceipt;
import com.ctu.model.Room;
import com.ctu.repository.RoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookingService Unit Test Suite")
class BookingServiceTest {

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private PaymentGateway paymentGateway;

    @InjectMocks
    private BookingService bookingService;

    // 1. Luồng thành công
    @Test
    @DisplayName("Đặt phòng thành công khi các thông tin hợp lệ và thanh toán thành công")
    void testBookRoom_Success() {
        // Arrange
        String roomId = "R101";
        int nights = 3;
        String creditCard = "1234-5678-9012";
        Room room = new Room(roomId, 100.0, true);

        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(paymentGateway.charge(creditCard, 300.0)).thenReturn(true);

        // Act
        BookingReceipt receipt = bookingService.bookRoom(roomId, nights, creditCard);

        // Assert
        assertNotNull(receipt);
        assertEquals(roomId, receipt.getRoomId());
        assertEquals(300.0, receipt.getTotalAmount(), 0.001);
        assertFalse(room.isAvailable(), "Trạng thái phòng phải chuyển thành false (đã đặt)");

        // Verification
        verify(roomRepository, times(1)).findById(roomId);
        verify(paymentGateway, times(1)).charge(creditCard, 300.0);
        verify(roomRepository, times(1)).save(room);
    }

    // 2. Kiểm thử Ngoại lệ Nghiệp vụ
    @Test
    @DisplayName("Ném RoomNotAvailableException khi không tìm thấy phòng (findById trả về Optional.empty)")
    void testBookRoom_RoomNotFound_ThrowsException() {
        // Arrange
        String roomId = "R999";
        when(roomRepository.findById(roomId)).thenReturn(Optional.empty());

        // Act & Assert
        RoomNotAvailableException exception = assertThrows(
                RoomNotAvailableException.class,
                () -> bookingService.bookRoom(roomId, 2, "1234-5678")
        );

        assertEquals("Khong tim thay phong: " + roomId, exception.getMessage());
        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném RoomNotAvailableException khi phòng đã được đặt trước đó (isAvailable == false)")
    void testBookRoom_RoomAlreadyBooked_ThrowsException() {
        // Arrange
        String roomId = "R102";
        Room room = new Room(roomId, 150.0, false); // Phòng đã hết/bị đặt
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));

        // Act & Assert
        RoomNotAvailableException exception = assertThrows(
                RoomNotAvailableException.class,
                () -> bookingService.bookRoom(roomId, 1, "1234-5678")
        );

        assertEquals("Phong da duoc dat truoc do: " + roomId, exception.getMessage());
        verify(paymentGateway, never()).charge(anyString(), anyDouble());
        verify(roomRepository, never()).save(any());
    }

    @Test
    @DisplayName("Ném PaymentFailedException khi thanh toán qua thẻ thất bại (charge trả về false)")
    void testBookRoom_PaymentFailed_ThrowsException() {
        // Arrange
        String roomId = "R103";
        Room room = new Room(roomId, 200.0, true);
        when(roomRepository.findById(roomId)).thenReturn(Optional.of(room));
        when(paymentGateway.charge(anyString(), anyDouble())).thenReturn(false);

        // Act & Assert
        PaymentFailedException exception = assertThrows(
                PaymentFailedException.class,
                () -> bookingService.bookRoom(roomId, 2, "1234-5678")
        );

        assertEquals("Thanh toan qua the credit card that bai!", exception.getMessage());
        assertTrue(room.isAvailable(), "Trạng thái phòng vẫn phải là true do thanh toán thất bại");
        verify(roomRepository, never()).save(any());
    }

    // 3. Kiểm thử Tham số Biên & Trường hợp ngoại lệ tham số (Edge Cases)
    @ParameterizedTest
    @ValueSource(ints = {0, -1, -5})
    @DisplayName("Ném IllegalArgumentException khi số đêm (nights) <= 0")
    void testBookRoom_InvalidNights_ThrowsException(int invalidNights) {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookRoom("R101", invalidNights, "1234-5678")
        );

        assertEquals("Tham so dat phong khong hop le!", exception.getMessage());
        verifyNoInteractions(roomRepository, paymentGateway);
    }

    @Test
    @DisplayName("Ném IllegalArgumentException khi creditCard null, rỗng hoặc chỉ chứa khoảng trắng")
    void testBookRoom_NullOrEmptyCreditCard_ThrowsException() {
        // Act & Assert cho null creditCard
        assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookRoom("R101", 2, null)
        );

        // Act & Assert cho creditCard rỗng
        assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookRoom("R101", 2, "   ")
        );

        verifyNoInteractions(roomRepository, paymentGateway);
    }

    @Test
    @DisplayName("Ném IllegalArgumentException khi roomId null")
    void testBookRoom_NullRoomId_ThrowsException() {
        // Act & Assert
        assertThrows(
                IllegalArgumentException.class,
                () -> bookingService.bookRoom(null, 2, "1234-5678")
        );

        verifyNoInteractions(roomRepository, paymentGateway);
    }
}