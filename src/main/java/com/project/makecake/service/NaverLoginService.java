package com.project.makecake.service;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.project.makecake.enums.UserRoleEnum;
import com.project.makecake.model.User;
import com.project.makecake.repository.UserRepository;
import com.project.makecake.security.JwtProperties;
import com.project.makecake.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletResponse;
import java.util.Date;
import java.util.UUID;

@RequiredArgsConstructor
@Service
public class NaverLoginService {

    @Value("${naver.client-id}")
    String naverClientId;

    @Value("${naver.client-secret}")
    String naverClientSecret;

    private final BCryptPasswordEncoder passwordEncoder;
    private final UserRepository userRepository;

    // 네이버 로그인
    public void naverLogin(String code, String state, HttpServletResponse response3) throws JsonProcessingException {
        // 인가코드로 엑세스토큰 가져오기
        String accessToken = getAccessToken(code, state);

        // 엑세스토큰으로 유저정보 가져오기
        JsonNode naverUserInfo = getNaverUserInfo(accessToken);

        // 유저확인 & 회원가입
        User foundUser = getUser(naverUserInfo);

        // 시큐리티 강제 로그인
        UserDetailsImpl userDetails = securityLogin(foundUser);

        // jwt 토큰 발급
        jwtToken(response3, userDetails);
    }

    // 인가코드로 엑세스토큰 가져오기
    private String getAccessToken(String code, String state) throws JsonProcessingException {
        // 헤더에 Content-type 지정
        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // 바디에 필요한 정보 담기
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("client_id", naverClientId);
        body.add("client_secret", naverClientSecret);
        body.add("code", code);
        body.add("state", state);

        // POST 요청 보내기
        HttpEntity<MultiValueMap<String, String>> naverToken = new HttpEntity<>(body, headers);
        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<String> response = restTemplate.exchange("https://nid.naver.com/oauth2.0/token", HttpMethod.POST, naverToken, String.class);

        // response에서 엑세스토큰 가져오기
        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode responseToken = objectMapper.readTree(responseBody);
        String accessToken = responseToken.get("access_token").asText();
        return accessToken;
    }

    // 엑세스토큰으로 유저정보 가져오기
    private JsonNode getNaverUserInfo(String accessToken) throws JsonProcessingException {
        // 헤더에 엑세스토큰 담기, Content-type 지정
        HttpHeaders headers = new HttpHeaders();
        headers.add(JwtProperties.HEADER_STRING, JwtProperties.TOKEN_PREFIX + accessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        // POST 요청 보내기
        HttpEntity<MultiValueMap<String, String>> naverUser = new HttpEntity<>(headers);
        RestTemplate restTemplate2 = new RestTemplate();
        ResponseEntity<String> response = restTemplate2.exchange("https://openapi.naver.com/v1/nid/me", HttpMethod.POST, naverUser, String.class);

        // response에서 유저정보 가져오기
        String responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode naverUserInfo = objectMapper.readTree(responseBody);
        return naverUserInfo;
    }

    // 유저확인 & 회원가입
    private User getUser(JsonNode responseInfo) {
        // 유저정보 작성
        String providerId = responseInfo.get("response").get("id").asText();
        String providerEmail = responseInfo.get("response").get("email").asText();
        String provider = "naver";
        String username = provider + "_" + providerId;
        String nickname = provider + "_" + providerId;
        String password = passwordEncoder.encode(UUID.randomUUID().toString());
        String profileImgUrl = "https://makecake.s3.ap-northeast-2.amazonaws.com/PROFILE/ef771589-abc6-4ddd-951c-73cc2420aa2fKakaoTalk_20220329_214148108.png";
        UserRoleEnum role = UserRoleEnum.USER;

        // DB에서 username으로 가져오기 없으면 회원가입
        User foundUser = userRepository.findByUsername(username).orElse(null);
        if (foundUser == null) {
            foundUser = User.builder()
                    .username(username)
                    .nickname(nickname)
                    .password(password)
                    .profileImgUrl(profileImgUrl)
                    .profileImgName(null)
                    .role(role)
                    .provider(provider)
                    .providerId(providerId)
                    .providerEmail(providerEmail)
                    .build();
            userRepository.save(foundUser);
        }
        return foundUser;
    }

    // 시큐리티 강제 로그인
    private UserDetailsImpl securityLogin(User foundUser) {
        // userDetails 생성
        UserDetailsImpl userDetails = new UserDetailsImpl(foundUser);
        System.out.println("naver 로그인 완료 : " + userDetails.getUser().getUsername());
        // UsernamePasswordAuthenticationToken 발급
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        // 강제로 시큐리티 세션에 접근하여 authentication 객체를 저장
        SecurityContextHolder.getContext().setAuthentication(authentication);
        return userDetails;
    }

    // jwt 토큰 발급
    private void jwtToken(HttpServletResponse response, UserDetailsImpl userDetails) {
        String jwtToken = JWT.create()
                // 토큰이름
                .withSubject("JwtToken : " + userDetails.getUser().getUsername())
                // 유효시간
                .withClaim("expireDate", new Date(System.currentTimeMillis() + JwtProperties.tokenValidTime))
                // username
                .withClaim("username", userDetails.getUser().getUsername())
                // HMAC256 복호화
                .sign(Algorithm.HMAC256(JwtProperties.secretKey));
        System.out.println("jwtToken : " + jwtToken);
        response.addHeader(JwtProperties.HEADER_STRING, JwtProperties.TOKEN_PREFIX + jwtToken);
    }
}
