package com.okulservisi.backend.user.controller;

import com.okulservisi.backend.common.web.PageResponse;
import com.okulservisi.backend.common.web.SortAllowlist;
import com.okulservisi.backend.security.annotation.AdminOnly;
import com.okulservisi.backend.user.api.UserApiPaths;
import com.okulservisi.backend.user.dto.request.UserCreateRequest;
import com.okulservisi.backend.user.dto.response.UserCreateResponse;
import com.okulservisi.backend.user.dto.response.UserResponse;
import com.okulservisi.backend.user.dto.request.UserRoleChangeRequest;
import com.okulservisi.backend.user.dto.request.UserUpdateRequest;
import com.okulservisi.backend.user.entity.Role;
import com.okulservisi.backend.user.entity.User;
import com.okulservisi.backend.user.mapper.UserMapper;
import com.okulservisi.backend.user.service.UserService;
import jakarta.validation.Valid;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** docs/API.md "2. Kullanıcı yönetimi" — tamamı ADMIN-only (bkz. docs/RBAC_MATRIX.md). */
@RestController
@RequestMapping(UserApiPaths.BASE)
@RequiredArgsConstructor
@AdminOnly
public class UserController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("id", "fullName", "email", "role", "createdAt");

    private final UserService userService;
    private final UserMapper userMapper;

    @GetMapping
    public PageResponse<UserResponse> list(
            @RequestParam(required = false) Role role,
            @RequestParam(name = "isActive", required = false) Boolean isActive,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean studentLinked,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String sort) {
        Pageable pageable = SortAllowlist.buildPageable(page, size, sort, SORTABLE_FIELDS, "id,asc");
        Page<User> result = userService.list(role, isActive, q, studentLinked, pageable);
        return PageResponse.of(result, result.getContent().stream().map(userMapper::toResponse).toList());
    }

    @GetMapping(UserApiPaths.BY_ID)
    public ResponseEntity<UserResponse> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userMapper.toResponse(userService.getById(id)));
    }

    @PostMapping
    public ResponseEntity<UserCreateResponse> create(@Valid @RequestBody UserCreateRequest request) {
        return ResponseEntity.ok(userService.create(request));
    }

    @PatchMapping(UserApiPaths.BY_ID)
    public ResponseEntity<UserResponse> update(@PathVariable Long id, @RequestBody UserUpdateRequest request) {
        return ResponseEntity.ok(userMapper.toResponse(userService.update(id, request)));
    }

    /**
     * docs/API.md "2. Kullanıcı yönetimi" — rol değişikliğini (ve DRIVER/HOSTESS/PARENT için
     * gerekli domain profilinin oluşturulmasını/yeniden kullanılmasını) TEK bir veritabanı
     * transaction'ında garanti eden hedefli uç. {@code PATCH /api/users/{id}} artık {@code role}
     * alanını KABUL ETMEZ (bkz. {@code UserService#update} javadoc'u) — rol değişikliği SADECE
     * buradan yapılır.
     */
    @PostMapping(UserApiPaths.CHANGE_ROLE)
    public ResponseEntity<UserResponse> changeRole(@PathVariable Long id, @Valid @RequestBody UserRoleChangeRequest request) {
        return ResponseEntity.ok(userMapper.toResponse(userService.changeRole(id, request)));
    }

    @PostMapping(UserApiPaths.DEACTIVATE)
    public ResponseEntity<UserResponse> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(userMapper.toResponse(userService.deactivate(id)));
    }

    @PostMapping(UserApiPaths.ACTIVATE)
    public ResponseEntity<UserResponse> activate(@PathVariable Long id) {
        return ResponseEntity.ok(userMapper.toResponse(userService.activate(id)));
    }
}
