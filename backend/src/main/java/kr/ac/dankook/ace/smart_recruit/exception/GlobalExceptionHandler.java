package kr.ac.dankook.ace.smart_recruit.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 모든 컨트롤러의 예외를 처리
public class GlobalExceptionHandler {

    // 1. 잘못된 인자가 들어왔을 때
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> handleIllegalArgumentException(IllegalArgumentException e) {
        // 400 Bad Request와 함께 에러 메시지를 보냄
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
    }

    // 2. 권한이 없는 작업을 시도할 때 (예: 내 계정이 아닌데 삭제 시도)
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> handleIllegalStateException(IllegalStateException e) {
        // 403 Forbidden을 보냄
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }

    // 3. @Valid 검증 실패 시 발생하는 예외 처리
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<String> handleValidationExceptions(MethodArgumentNotValidException e) {
        // 첫 번째 에러 메시지만 가져와서 리턴
        String message = e.getBindingResult().getAllErrors().get(0).getDefaultMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(message);
    }

    // 4. 데이터베이스 제약 조건 위반 (예: 중복된 이메일이나 닉네임)
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity <String> handleDataIntegrityViolation(DataIntegrityViolationException e){
        return ResponseEntity.status(HttpStatus.CONFLICT)
            .body("이미 존재하는 이메일이나 닉네임입니다.");
    }

    //  그 외 예상치 못한 모든 에러
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAllException(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("서버 내부 오류가 발생했습니다.");
    }
}