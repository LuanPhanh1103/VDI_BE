package com.mobifone.vdi.service;

import com.mobifone.vdi.dto.request.AuthenticationRequest;
import com.mobifone.vdi.dto.request.IntrospectRequest;
import com.mobifone.vdi.dto.request.LogoutRequest;
import com.mobifone.vdi.dto.request.RefreshRequest;
import com.mobifone.vdi.dto.response.AuthenticationResponse;
import com.mobifone.vdi.dto.response.IntrospectResponse;
import com.mobifone.vdi.dto.response.PermissionResponse;
import com.mobifone.vdi.dto.response.RoleResponse;
import com.mobifone.vdi.entity.InvalidatedToken;
import com.mobifone.vdi.entity.User;
import com.mobifone.vdi.exception.AppException;
import com.mobifone.vdi.exception.ErrorCode;
import com.mobifone.vdi.repository.InvalidatedTokenRepository;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import java.util.LinkedHashSet;

import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AuthenticationService {
    UserService userService;
    InvalidatedTokenRepository invalidatedTokenRepository;

    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    @NonFinal
    @Value("${jwt.valid-duration}")
    protected long VALID_DURATION;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    protected long REFRESHABLE_DURATION;

    public IntrospectResponse introspect(IntrospectRequest request) throws JOSEException, ParseException {
        var token = request.getToken();
        boolean isValid = true;

        try {
            verifyToken(token, false);
        } catch (AppException e) {
            isValid = false;
        }

        return IntrospectResponse.builder().valid(isValid).build();
    }

//    public AuthenticationResponse authenticate(AuthenticationRequest request) {
//        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
//        var user = userService.findUserByUsername(request.getUsername());
//
//        Set<Permission> permissions = user.getRoles().stream()
//                .flatMap(role -> role.getPermissions().stream())
//                .collect(Collectors.toSet());
//
//        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
//
//        if (!authenticated) throw new AppException(ErrorCode.WRONG_PASSWORD);
//
//        var token = generateToken(user);
//
//        return AuthenticationResponse.builder()
//                .token(token)
//                .userId(user.getId())
//                .username(user.getUsername())
//                .firstName(user.getFirstName())
//                .lastName(user.getLastName())
//                .email(user.getEmail())
//                .permissions(permissions)
//                .authenticated(true)
//                .build();
//    }
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        var user = userService.findUserByUsernameROLE(request.getUsername());

        boolean authenticated = passwordEncoder.matches(request.getPassword(), user.getPassword());
        if (!authenticated) throw new AppException(ErrorCode.WRONG_PASSWORD);

        var token = generateToken(user);

        // map roles -> RoleResponse (kèm permissions -> PermissionResponse)
        Set<RoleResponse> roleResponses = user.getRoles().stream()
                .map(role -> RoleResponse.builder()
                        .name(role.getName())
                        .description(role.getDescription())
                        .permissions(
                                role.getPermissions().stream()
                                        .map(p -> PermissionResponse.builder()
                                                .name(p.getName())
                                                .description(p.getDescription())
                                                .build()
                                        )
                                        .collect(Collectors.toCollection(LinkedHashSet::new))
                        )
                        .build()
                )
                .collect(Collectors.toCollection(LinkedHashSet::new));

        return AuthenticationResponse.builder()
                .token(token)
                .userId(user.getId())
                .username(user.getUsername())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .email(user.getEmail())
                .roles(roleResponses)
                .authenticated(true)
                .build();
    }


    public void logout(LogoutRequest request) throws ParseException, JOSEException {
        try {
            var signToken = verifyToken(request.getToken(), true);

            String jit = signToken.getJWTClaimsSet().getJWTID();
            Date expiryTime = signToken.getJWTClaimsSet().getExpirationTime();

            InvalidatedToken invalidatedToken =
                    InvalidatedToken.builder().id(jit).expiryTime(expiryTime).build();

            invalidatedTokenRepository.save(invalidatedToken);
        } catch (AppException exception) {
            log.info("Token already expired");
        }
    }

    public AuthenticationResponse refreshToken(RefreshRequest request) throws ParseException, JOSEException {
        var signedJWT = verifyToken(request.getToken(), true);

        var jit = signedJWT.getJWTClaimsSet().getJWTID();
        var expiryTime = signedJWT.getJWTClaimsSet().getExpirationTime();

        InvalidatedToken invalidatedToken =
                InvalidatedToken.builder().id(jit).expiryTime(expiryTime).build();

        invalidatedTokenRepository.save(invalidatedToken);

        var username = signedJWT.getJWTClaimsSet().getSubject();

        var user = userService.findUserByUsername(username);
        var token = generateToken(user);

        return AuthenticationResponse.builder().token(token).authenticated(true).build();
    }

    private String generateToken(User user) {
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getUsername())
                .issuer("mobifone.com")
                .issueTime(new Date())
                .expirationTime(new Date(
                        Instant.now().plus(VALID_DURATION, ChronoUnit.SECONDS).toEpochMilli()))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", buildScope(user))
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Cannot create token", e);
            throw new RuntimeException(e);
        }
    }

    private SignedJWT verifyToken(String token, boolean isRefresh) throws JOSEException, ParseException {
        JWSVerifier verifier = new MACVerifier(SIGNER_KEY.getBytes());

        SignedJWT signedJWT = SignedJWT.parse(token);

        Date expiryTime = (isRefresh)
                ? new Date(signedJWT
                        .getJWTClaimsSet()
                        .getIssueTime()
                        .toInstant()
                        .plus(REFRESHABLE_DURATION, ChronoUnit.SECONDS)
                        .toEpochMilli())
                : signedJWT.getJWTClaimsSet().getExpirationTime();

        var verified = signedJWT.verify(verifier);

        if (!(verified && expiryTime.after(new Date()))) throw new AppException(ErrorCode.UNAUTHENTICATED);

        if (invalidatedTokenRepository.existsById(signedJWT.getJWTClaimsSet().getJWTID()))
            throw new AppException(ErrorCode.UNAUTHENTICATED);

        return signedJWT;
    }

//    private String buildScope(User user) {
//        StringJoiner stringJoiner = new StringJoiner(" ");
//
//        if (!CollectionUtils.isEmpty(user.getRoles()))
//            user.getRoles().forEach(role -> {
//                stringJoiner.add("ROLE_" + role.getName());
//                if (!CollectionUtils.isEmpty(role.getPermissions()))
//                    role.getPermissions().forEach(permission -> stringJoiner.add(permission.getName()));
//            });
//
//        return stringJoiner.toString();
//    }
    private String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");

        // Duyệt qua tất cả các vai trò của user
        if (!CollectionUtils.isEmpty(user.getRoles())) {
            user.getRoles().forEach(role -> {
                // Lấy quyền từ mỗi role và gán ROLE_ vào trước
                if (!CollectionUtils.isEmpty(role.getPermissions())) {
                    role.getPermissions().forEach(permission -> {
                        // Gán ROLE_ vào mỗi permission
                        stringJoiner.add("ROLE_" + permission.getName());
                    });
                }
            });
        }

        return stringJoiner.toString();
    }
}
