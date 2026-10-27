# Read Me First
The following was discovered as part of building this project:

* The original package name 'com.example.cart-rush' is invalid and this project uses 'com.example.cart_rush' instead.

# Getting Started

### Reference Documentation
For further reference, please consider the following sections:

* [Official Apache Maven documentation](https://maven.apache.org/guides/index.html)
* [Spring Boot Maven Plugin Reference Guide](https://docs.spring.io/spring-boot/4.1.1/maven-plugin)
* [Create an OCI image](https://docs.spring.io/spring-boot/4.1.1/maven-plugin/build-image.html)
* [Spring Web](https://docs.spring.io/spring-boot/4.1.1/reference/web/servlet.html)
* [Spring Data JPA](https://docs.spring.io/spring-boot/4.1.1/reference/data/sql.html#data.sql.jpa-and-spring-data)
* [Validation](https://docs.spring.io/spring-boot/4.1.1/reference/io/validation.html)

### Guides
The following guides illustrate how to use some features concretely:

* [Building a RESTful Web Service](https://spring.io/guides/gs/rest-service/)
* [Serving Web Content with Spring MVC](https://spring.io/guides/gs/serving-web-content/)
* [Building REST services with Spring](https://spring.io/guides/tutorials/rest/)
* [Accessing Data with JPA](https://spring.io/guides/gs/accessing-data-jpa/)
* [Accessing data with MySQL](https://spring.io/guides/gs/accessing-data-mysql/)
* [Validation](https://spring.io/guides/gs/validating-form-input/)

### Maven Parent overrides

Due to Maven's design, elements are inherited from the parent POM to the project POM.
While most of the inheritance is fine, it also inherits unwanted elements like `<license>` and `<developers>` from the parent.
To prevent this, the project POM contains empty overrides for these elements.
If you manually switch to a different parent and actually want the inheritance, you need to remove those overrides.

## Shopping Assistant (Gemini)

The storefront assistant uses Google's official `google-genai` Java SDK to extract shopping filters with Gemini, then returns matching products from the local Cart Rush catalogue. Product replies and ratings are based on catalogue and review records. If Gemini is not configured or is unavailable, a deterministic local parser still handles product searches.

Set the API key in the environment before starting the application. Do not commit the key:

```powershell
$env:GEMINI_API_KEY = "your-gemini-api-key"
.\mvnw.cmd spring-boot:run
```

Optional settings can be supplied with environment variables:

| Variable | Default | Purpose |
| --- | --- | --- |
| `APP_ASSISTANT_PROVIDER` | `gemini` | Assistant provider selection |
| `APP_ASSISTANT_GEMINI_MODEL` | `gemini-3.8-flash` | Gemini model name |
| `GEMINI_API_KEY` | empty | Gemini API key; fallback search works without it |

The synchronous compatibility endpoint is `POST /api/assistant/chat`. The storefront uses `POST /api/assistant/chat/stream` with JSON such as:

```json
{"message":"waterproof hiking gear under 5000, rated 4 stars or higher","history":[{"role":"user","content":"Hi"},{"role":"assistant","content":"Hi! What are you shopping for?"}]}
```

The stream emits named SSE events: `status`, zero or more `token` chunks, then `final` with the response object; unrecoverable stream failures emit `error`. Intent is classified before catalogue lookup: `GREETING` and `GENERAL_QUESTION` do not trigger search, `SHOPPING_CLEAR` extracts filters and searches, `SHOPPING_VAGUE` asks for clarification, and `UNSUPPORTED` redirects to shopping. `ACCOUNT_CART` (“show my cart”) and `ACCOUNT_ORDERS` (“show my previous purchases”) read the signed-in customer’s cart and order history; the storefront sends the logged-in `userId` in the request body (the same client-identified pattern as `/api/carts/user/{id}`, so a real deployment should derive identity server-side). Cart and order contents are never sent to the model — those replies are composed locally from database facts, and when no customer is logged in the assistant asks them to log in instead of querying. For example, “hi” gets a greeting; “in-stock products under 1000” searches available catalogue items within budget. Generic words such as “products”, “items”, and “in stock” are treated as filter language, not search keywords. Product explanations only receive server-selected catalogue facts. If there are no exact matches, closest alternatives keep stock, budget, and waterproof constraints intact and disclose any relaxed category/brand/keyword/rating filters.

The final response contains `reply`, catalogue-backed `products`, extracted `filters`, `source`, `intent`, provenance flags, and optional `relaxedFilters`; account intents additionally return a `cart` block (items with quantity, unit price, line totals, and grand total) or an `orders` list (status, date, items, total). Recent chat context is bounded to the last 10 turns and retained in browser `sessionStorage` only; chats are not saved in MySQL. If fallback is used, `aiFallbackReason` provides a sanitized code such as `HTTP_401`, `HTTP_429`, `KEY_NOT_CONFIGURED`, or `INVALID_MODEL_INTENT`; it never contains the key or provider response body. Connection and response timeouts are configurable through `app.assistant.connect-timeout` and `app.assistant.read-timeout`.

