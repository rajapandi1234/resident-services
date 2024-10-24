package io.mosip.resident.aspect;

import com.auth0.jwt.JWT;
import com.auth0.jwt.interfaces.Claim;
import io.mosip.kernel.core.logger.spi.Logger;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.resident.config.LoggerConfiguration;
import io.mosip.resident.entity.ResidentSessionEntity;
import io.mosip.resident.exception.ApisResourceAccessException;
import io.mosip.resident.exception.ResidentServiceCheckedException;
import io.mosip.resident.repository.ResidentSessionRepository;
import io.mosip.resident.service.impl.IdentityServiceImpl;
import io.mosip.resident.util.AuditUtil;
import io.mosip.resident.util.AuditEnum;
import io.mosip.resident.util.Utility;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.HttpCookie;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Aspect class for login redirect API
 * 
 * @author Ritik Jain
 */
@Component
@Aspect
@EnableAspectJAutoProxy
public class LoginCheck {

	private static final String SET_COOKIE = "Set-Cookie";
	private static final String USER_AGENT = "User-Agent";
	private static final String WINDOWS = "Windows";
	private static final String MAC = "Mac";
	private static final String UNIX = "Unix";
	private static final String X11 = "x11";
	private static final String ANDROID = "Android";
	private static final String IPHONE = "IPhone";
	private static final CharSequence AUTHORIZATION_TOKEN = "Authorization";
	private static final int resStatusCode = 302;

	@Autowired
	private ResidentSessionRepository residentSessionRepository;
	
	@Autowired
	private IdentityServiceImpl identityServiceImpl;
	
	@Autowired
	private Utility utility;
	
	@Autowired
	private AuditUtil audit;

	@Autowired
	private ThreadPoolTaskScheduler taskScheduler;
	
	@Value("${auth.token.header:Authorization}")
	private String authTokenHeader;

	@Value("${mosip.resident.oidc.auth_token.expiry.claim-name:exp}")
	private String expiryClaimName;
	
	private static final Logger logger = LoggerConfiguration.logConfig(LoginCheck.class);

	@After("execution(* io.mosip.kernel.authcodeflowproxy.api.controller.LoginController.loginRedirect(..)) && args(redirectURI,state,sessionState,code,stateCookie,req,res)")
	public void getUserDetails(String redirectURI, String state, String sessionState, String code, String stateCookie,
			HttpServletRequest req, HttpServletResponse res) throws ResidentServiceCheckedException, ApisResourceAccessException {
		logger.debug("LoginCheck::getUserDetails()::entry");
		String idaToken = "";
		String sessionId = "";
		Collection<String> cookies = res.getHeaders(SET_COOKIE);
		for (String cookie : cookies) {
			if (cookie.contains(AUTHORIZATION_TOKEN)) {
				Optional<String> authorizationCookie = getCookieValueFromHeader(cookie);
				if (authorizationCookie.isPresent()) {
					String accessToken = authorizationCookie.get();
					Claim decodedToken = JWT.decode(accessToken).getClaim(expiryClaimName);
					Date expDate = decodedToken.asDate();
					logger.info("Scheduling clearing auth token cache after : " + expDate);
					taskScheduler.schedule(() -> {
						if(accessToken!=null && !accessToken.isEmpty()) {
							utility.clearUserInfoCache(accessToken);
							utility.clearIdentityMapCache(accessToken);
						}
					}, expDate);
					idaToken = identityServiceImpl.getResidentIdaTokenFromAccessToken(accessToken);
					sessionId = identityServiceImpl.createSessionId();
				}
				break;
			}
		}
		if(idaToken!=null && !idaToken.isEmpty() && sessionId != null && !sessionId.isEmpty()) {
			audit.setAuditRequestDto(AuditEnum.LOGIN_REQ_SUCCESS);
			ResidentSessionEntity newSessionData = new ResidentSessionEntity(sessionId, idaToken, DateUtils.getUTCCurrentDateTime(),
					utility.getClientIp(req), req.getRemoteHost(), getMachineType(req));
			residentSessionRepository.save(newSessionData);
		} else {
			audit.setAuditRequestDto(AuditEnum.LOGIN_REQ_FAILURE);
		}
		logger.debug("LoginCheck::getUserDetails()::exit");
	}

	@After("execution(* io.mosip.kernel.authcodeflowproxy.api.controller.LoginController.loginRedirect(..)) && args(redirectURI,state,sessionState,code,error,stateCookie,req,res)")
	public void getUserDetails(String redirectURI, String state, String sessionState, String code, String error, String stateCookie,
							   HttpServletRequest req, HttpServletResponse res) throws ResidentServiceCheckedException, ApisResourceAccessException {
		getUserDetails(redirectURI, state, sessionState, code, stateCookie, req, res);
	}


		@Pointcut(value = "execution(* io.mosip.kernel.authcodeflowproxy.api.controller.LoginController.login(..))")
	public void login() {
	}

	@AfterThrowing(pointcut = "login()", throwing = "e")
	public void onLoginReqFailure(RuntimeException e) {
		logger.debug("LoginCheck::onLoginReqFailure()::entry");
		audit.setAuditRequestDto(AuditEnum.LOGIN_REQ_FAILURE);
	}

	@Before("execution(* io.mosip.kernel.authcodeflowproxy.api.controller.LoginController.login(..)) && args(state,redirectURI,stateParam,res)")
	public void onLoginReq(String state, String redirectURI, String stateParam, HttpServletResponse res) {
		logger.debug("LoginCheck::onLoginReq()::entry");
		if (res.getStatus() == resStatusCode) {
			audit.setAuditRequestDto(AuditEnum.LOGIN_REQ);
		}
		logger.debug("LoginCheck::onLoginReq()::exit");
	}

	@Before("execution(* io.mosip.kernel.authcodeflowproxy.api.controller.LoginController.login(..)) && args(state,redirectURI,stateParam,uiLocales,res)")
	public void onLoginReq(String state, String redirectURI, String stateParam, String uiLocales, HttpServletResponse res) {
		onLoginReq(state, redirectURI, stateParam, res);
	}

		@Pointcut(value = "execution(* io.mosip.kernel.authcodeflowproxy.api.controller.LoginController.loginRedirect(..))")
	public void loginRedirect() {
	}

	@AfterThrowing(pointcut = "loginRedirect()", throwing = "e")
	public void onLoginFailure(RuntimeException e) {
		logger.debug("LoginCheck::onLoginFailure()::entry");
		audit.setAuditRequestDto(AuditEnum.LOGIN_REQ_FAILURE);
	}

	@After("execution(* io.mosip.kernel.authcodeflowproxy.api.controller.LoginController.logoutUser(..)) && args(token,redirectURI,res)")
	public void onLogoutSuccess(String token, String redirectURI, HttpServletResponse res) throws ApisResourceAccessException {
		logger.debug("LoginCheck::onLogoutSuccess()::entry");
		audit.setAuditRequestDto(AuditEnum.LOGOUT_REQ);
		if (res.getStatus() == resStatusCode) {
			audit.setAuditRequestDto(AuditEnum.LOGOUT_REQ_SUCCESS);
		} else {
			audit.setAuditRequestDto(AuditEnum.LOGOUT_REQ_FAILURE);
		}
		if(token!=null && !token.isEmpty()){
			utility.clearUserInfoCache(token);
			utility.clearIdentityMapCache(token);
		}
		logger.debug("LoginCheck::onLogoutSuccess()::exit");
	}

	@Pointcut(value = "execution(* io.mosip.kernel.authcodeflowproxy.api.controller.LoginController.logoutUser(..))")
	public void logoutUser() {
	}

	@AfterThrowing(pointcut = "logoutUser()", throwing = "e")
	public void onLogoutFailure(RuntimeException e) {
		logger.debug("LoginCheck::onLogoutFailure()::entry");
		audit.setAuditRequestDto(AuditEnum.LOGOUT_REQ_FAILURE);
	}

	@After("execution(* io.mosip.kernel.authcodeflowproxy.api.controller.LoginController.validateAdminToken(..)) && args(request,res)")
	public void onValidateTokenSuccess(HttpServletRequest request, HttpServletResponse res) {
		logger.debug("LoginCheck::onValidateTokenSuccess()::entry");
		String authToken = null;
		Cookie[] cookies = request.getCookies();
		for (Cookie cookie : cookies) {
			if (cookie.getName().contains(authTokenHeader)) {
				authToken = cookie.getValue();
				audit.setAuditRequestDto(AuditEnum.VALIDATE_TOKEN_SUCCESS);
			}
		}
		if (authToken == null) {
			audit.setAuditRequestDto(AuditEnum.VALIDATE_TOKEN_FAILURE);
		}
		logger.debug("LoginCheck::onValidateTokenSuccess()::exit");
	}

	@Pointcut(value = "execution(* io.mosip.kernel.authcodeflowproxy.api.controller.LoginController.validateAdminToken(..))")
	public void validateAdminToken() {
	}

	@AfterThrowing(pointcut = "validateAdminToken()", throwing = "e")
	public void onValidateTokenFailure(RuntimeException e) {
		logger.debug("LoginCheck::onValidateTokenFailure()::entry");
		audit.setAuditRequestDto(AuditEnum.VALIDATE_TOKEN_FAILURE);
	}
	
	private Optional<String> getCookieValueFromHeader(String cookie) {
		logger.debug("LoginCheck::getCookieValueFromHeader()::entry");
		List<HttpCookie> httpCookieList = HttpCookie.parse(cookie);
		if (!httpCookieList.isEmpty()) {
			HttpCookie httpCookie = httpCookieList.get(0);
			String value = httpCookie.getValue();
			logger.debug("LoginCheck::getCookieValueFromHeader()::exit");
			return Optional.of(value);
		}
		logger.debug("LoginCheck::getCookieValueFromHeader()::exit - cookie is empty");
		return Optional.empty();
	}

	private String getMachineType(HttpServletRequest req) {
		logger.debug("LoginCheck::getMachineType()::entry");
		String userAgent = req.getHeader(USER_AGENT);

		String os = "";
		if (userAgent.toLowerCase().indexOf(WINDOWS.toLowerCase()) >= 0) {
			os = WINDOWS;
		} else if (userAgent.toLowerCase().indexOf(MAC.toLowerCase()) >= 0) {
			os = MAC;
		} else if (userAgent.toLowerCase().indexOf(X11) >= 0) {
			os = UNIX;
		} else if (userAgent.toLowerCase().indexOf(ANDROID.toLowerCase()) >= 0) {
			os = ANDROID;
		} else if (userAgent.toLowerCase().indexOf(IPHONE.toLowerCase()) >= 0) {
			os = IPHONE;
		} else {
			os = "UnKnown, More-Info: " + userAgent;
		}
		logger.debug("LoginCheck::getMachineType()::exit");
		return os;
	}
}
