package com.poly.sof309.controllers;

import com.petcare.models.Users;
import com.petcare.utils.SessionUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory dynamic proxy harness for Jakarta Servlet API objects used in Unit Tests.
 * Allows direct testing of Servlets and Filters without Tomcat or extra mocking libraries.
 */
final class MockServletHelper {

    private MockServletHelper() {
    }

    static final class MockExchange {
        private final Map<String, String> parameters = new LinkedHashMap<>();
        private final Map<String, Object> requestAttributes = new LinkedHashMap<>();
        private final Map<String, Object> sessionAttributes = new LinkedHashMap<>();

        private String contextPath = "";
        private String requestPath = "/";
        private String requestEncoding = "UTF-8";
        private String responseEncoding = "UTF-8";

        private boolean sessionExists = false;
        private boolean sessionInvalidated = false;
        private int maxInactiveInterval = 1800;

        private String forwardedPath;
        private String includedPath;
        private String redirectedUrl;
        private int status = 200;
        private Integer errorStatus;
        private String errorMessage;
        private boolean chainProceeded = false;

        private final HttpSession sessionProxy;
        private final HttpServletRequest requestProxy;
        private final HttpServletResponse responseProxy;
        private final FilterChain filterChainProxy;

        MockExchange() {
            this.sessionProxy = createSessionProxy();
            this.requestProxy = createRequestProxy();
            this.responseProxy = createResponseProxy();
            this.filterChainProxy = createFilterChainProxy();
        }

        MockExchange withContextPath(String contextPath) {
            this.contextPath = contextPath != null ? contextPath : "";
            return this;
        }

        MockExchange withRequestPath(String requestPath) {
            this.requestPath = requestPath != null ? requestPath : "/";
            return this;
        }

        MockExchange withParam(String name, String value) {
            if (value == null) {
                parameters.remove(name);
            } else {
                parameters.put(name, value);
            }
            return this;
        }

        MockExchange withLoggedInUser(Users user) {
            ensureSession();
            SessionUtil.refreshSessionUser(requestProxy, user);
            return this;
        }

        MockExchange withSessionAttribute(String name, Object value) {
            ensureSession();
            sessionAttributes.put(name, value);
            return this;
        }

        HttpServletRequest request() {
            return requestProxy;
        }

        HttpServletResponse response() {
            return responseProxy;
        }

        FilterChain filterChain() {
            return filterChainProxy;
        }

        Object getRequestAttribute(String name) {
            return requestAttributes.get(name);
        }

        Object getSessionAttribute(String name) {
            return sessionExists ? sessionAttributes.get(name) : null;
        }

        boolean hasSession() {
            return sessionExists;
        }

        boolean isSessionInvalidated() {
            return sessionInvalidated;
        }

        int getMaxInactiveInterval() {
            return maxInactiveInterval;
        }

        String getForwardedPath() {
            return forwardedPath;
        }

        String getIncludedPath() {
            return includedPath;
        }

        String getRedirectedUrl() {
            return redirectedUrl;
        }

        int getStatus() {
            return status;
        }

        Integer getErrorStatus() {
            return errorStatus;
        }

        String getErrorMessage() {
            return errorMessage;
        }

        boolean isChainProceeded() {
            return chainProceeded;
        }

        private HttpSession ensureSession() {
            if (!sessionExists) {
                sessionAttributes.clear();
                sessionExists = true;
                sessionInvalidated = false;
            }
            return sessionProxy;
        }

        private HttpSession createSessionProxy() {
            return (HttpSession) Proxy.newProxyInstance(
                    MockServletHelper.class.getClassLoader(),
                    new Class<?>[]{HttpSession.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        return switch (name) {
                            case "getAttribute" -> sessionAttributes.get((String) args[0]);
                            case "setAttribute" -> {
                                String key = (String) args[0];
                                Object val = args[1];
                                if (val == null) {
                                    sessionAttributes.remove(key);
                                } else {
                                    sessionAttributes.put(key, val);
                                }
                                yield null;
                            }
                            case "removeAttribute" -> {
                                sessionAttributes.remove((String) args[0]);
                                yield null;
                            }
                            case "getAttributeNames" -> Collections.enumeration(sessionAttributes.keySet());
                            case "getMaxInactiveInterval" -> maxInactiveInterval;
                            case "setMaxInactiveInterval" -> {
                                maxInactiveInterval = (Integer) args[0];
                                yield null;
                            }
                            case "invalidate" -> {
                                sessionAttributes.clear();
                                sessionExists = false;
                                sessionInvalidated = true;
                                yield null;
                            }
                            case "getId" -> "mock-session-1";
                            case "isNew" -> false;
                            case "toString" -> "MockHttpSession";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> defaultValue(method.getReturnType());
                        };
                    });
        }

        private HttpServletRequest createRequestProxy() {
            return (HttpServletRequest) Proxy.newProxyInstance(
                    MockServletHelper.class.getClassLoader(),
                    new Class<?>[]{HttpServletRequest.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        return switch (name) {
                            case "getParameter" -> parameters.get((String) args[0]);
                            case "getParameterValues" -> {
                                String val = parameters.get((String) args[0]);
                                yield val != null ? new String[]{val} : null;
                            }
                            case "getParameterNames" -> Collections.enumeration(parameters.keySet());
                            case "getParameterMap" -> {
                                Map<String, String[]> map = new LinkedHashMap<>();
                                parameters.forEach((k, v) -> map.put(k, new String[]{v}));
                                yield map;
                            }
                            case "getAttribute" -> requestAttributes.get((String) args[0]);
                            case "setAttribute" -> {
                                String key = (String) args[0];
                                Object val = args[1];
                                if (val == null) {
                                    requestAttributes.remove(key);
                                } else {
                                    requestAttributes.put(key, val);
                                }
                                yield null;
                            }
                            case "removeAttribute" -> {
                                requestAttributes.remove((String) args[0]);
                                yield null;
                            }
                            case "getAttributeNames" -> Collections.enumeration(requestAttributes.keySet());
                            case "getSession" -> {
                                if (args == null || args.length == 0) {
                                    yield ensureSession();
                                }
                                boolean create = (Boolean) args[0];
                                yield create ? ensureSession() : (sessionExists ? sessionProxy : null);
                            }
                            case "getContextPath" -> contextPath;
                            case "getRequestURI" -> contextPath + requestPath;
                            case "getServletPath" -> requestPath;
                            case "getCharacterEncoding" -> requestEncoding;
                            case "setCharacterEncoding" -> {
                                requestEncoding = (String) args[0];
                                yield null;
                            }
                            case "getRequestDispatcher" -> createDispatcherProxy((String) args[0]);
                            case "toString" -> "MockHttpServletRequest(" + requestPath + ")";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> defaultValue(method.getReturnType());
                        };
                    });
        }

        private RequestDispatcher createDispatcherProxy(String path) {
            return (RequestDispatcher) Proxy.newProxyInstance(
                    MockServletHelper.class.getClassLoader(),
                    new Class<?>[]{RequestDispatcher.class},
                    (proxy, method, args) -> {
                        if ("forward".equals(method.getName())) {
                            forwardedPath = path;
                            return null;
                        }
                        if ("include".equals(method.getName())) {
                            includedPath = path;
                            return null;
                        }
                        return defaultValue(method.getReturnType());
                    });
        }

        private HttpServletResponse createResponseProxy() {
            return (HttpServletResponse) Proxy.newProxyInstance(
                    MockServletHelper.class.getClassLoader(),
                    new Class<?>[]{HttpServletResponse.class},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        return switch (name) {
                            case "sendRedirect" -> {
                                redirectedUrl = (String) args[0];
                                status = HttpServletResponse.SC_FOUND;
                                yield null;
                            }
                            case "sendError" -> {
                                errorStatus = (Integer) args[0];
                                status = errorStatus;
                                if (args.length > 1) {
                                    errorMessage = (String) args[1];
                                }
                                yield null;
                            }
                            case "setStatus" -> {
                                status = (Integer) args[0];
                                yield null;
                            }
                            case "getStatus" -> status;
                            case "getCharacterEncoding" -> responseEncoding;
                            case "setCharacterEncoding" -> {
                                responseEncoding = (String) args[0];
                                yield null;
                            }
                            case "encodeRedirectURL", "encodeURL" -> (String) args[0];
                            case "toString" -> "MockHttpServletResponse";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == args[0];
                            default -> defaultValue(method.getReturnType());
                        };
                    });
        }

        private FilterChain createFilterChainProxy() {
            return (ServletRequest req, ServletResponse resp) -> chainProceeded = true;
        }

        private static Object defaultValue(Class<?> returnType) {
            if (returnType == void.class) {
                return null;
            }
            if (returnType == boolean.class) {
                return false;
            }
            if (returnType == int.class) {
                return 0;
            }
            if (returnType == long.class) {
                return 0L;
            }
            if (returnType == double.class) {
                return 0.0;
            }
            if (returnType == float.class) {
                return 0.0f;
            }
            if (returnType == short.class) {
                return (short) 0;
            }
            if (returnType == byte.class) {
                return (byte) 0;
            }
            if (returnType == char.class) {
                return '\0';
            }
            if ("java.util.Enumeration".equals(returnType.getName())) {
                return Collections.emptyEnumeration();
            }
            return null;
        }
    }
}
