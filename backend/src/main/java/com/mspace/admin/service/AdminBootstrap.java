package com.mspace.admin.service;

import com.mspace.user.domain.User;
import com.mspace.user.domain.UserRole;
import com.mspace.user.domain.UserStatus;
import com.mspace.user.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 첫 배포 때 관리자 계정 만들기 (AU-06). 관리자는 회원가입으로 만들 수 없어서, 서버 환경 변수
 * BOOTSTRAP_ADMIN_STUDENT_NO·BOOTSTRAP_ADMIN_PASSWORD가 있고 활성 관리자가 한 명도 없을 때만 한 번 만들어요.
 *
 * <ul>
 *   <li>만든 계정은 임시 비밀번호 상태라 첫 로그인 뒤 비밀번호를 바꾸기 전에는 다른 기능을 못 써요 (AU-07).
 *       → 환경 변수에 남은 값은 곧 쓸모없어져요. 계정을 만든 뒤에는 환경 변수를 지워요(배포 가이드).</li>
 *   <li>관리자가 이미 있으면 아무것도 하지 않아요 → 재시작해도 계정이 늘거나 비밀번호가 바뀌지 않아요(멱등).</li>
 *   <li>비밀번호는 로그에 남기지 않아요 (NF-01).</li>
 * </ul>
 */
@Slf4j
@Component
public class AdminBootstrap implements ApplicationRunner {

    /** 관리자 계정은 연락처가 필요 없어서 형식만 맞춘 값이에요. 첫 로그인 뒤 내 정보에서 바꿀 수 있어요 */
    static final String PLACEHOLDER_PHONE = "000-0000-0000";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String studentNo;
    private final String password;
    private final String name;

    public AdminBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder,
                          @Value("${mspace.bootstrap-admin.student-no:}") String studentNo,
                          @Value("${mspace.bootstrap-admin.password:}") String password,
                          @Value("${mspace.bootstrap-admin.name:관리자}") String name) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.studentNo = studentNo;
        this.password = password;
        this.name = name;
    }

    @Override
    public void run(ApplicationArguments args) {
        createIfAbsent(studentNo, password, name);
    }

    /** @return 새로 만들었으면 true */
    @Transactional
    public boolean createIfAbsent(String studentNo, String password, String name) {
        if (studentNo == null || studentNo.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        if (userRepository.existsByRoleAndStatus(UserRole.ADMIN, UserStatus.ACTIVE)) {
            return false;
        }
        if (password.length() < 8) {
            log.warn("초기 관리자 비밀번호가 8자보다 짧아서 계정을 만들지 않았어요");
            return false;
        }
        User admin = User.create(studentNo.strip(), name == null || name.isBlank() ? "관리자" : name.strip(),
                PLACEHOLDER_PHONE, passwordEncoder.encode(password), UserRole.ADMIN);
        admin.resetPassword(admin.getPasswordHash());   // 임시 비밀번호 상태로 → 첫 로그인 뒤 변경 강제 (AU-07)
        userRepository.saveAndFlush(admin);
        log.info("초기 관리자 계정을 만들었어요. 첫 로그인 뒤 비밀번호를 바꿔야 해요");
        return true;
    }
}
