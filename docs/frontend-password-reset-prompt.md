# Frontend prompt: forgot-password email flow

Implement the Groovsta forgot-password flow using the existing public API. Do not add
a new endpoint and do not send the reset code from the client.

## Flow

1. On the login screen, make **Forgot password?** open a reset screen.
2. Let the user choose **Email** or **Phone**. Start with Email selected.
3. Validate the selected field locally. Email must be a valid email address; phone must
   be E.164. Submit exactly one field to `POST /api/v1/auth/forgot-password`.
4. On `200`, navigate to a code screen and show the masked `destination` and the
   `expiresAt` countdown from the response. Tell the user to check their email when
   Email is selected.
5. On the code screen collect the six-digit code and a new password (8–128 characters).
   Submit exactly one of `email` or `phoneNumber`, plus `code` and `newPassword`, to
   `POST /api/v1/auth/reset-password`.
6. On `204`, clear reset state, show a success message, and return to login. Do not
   automatically authenticate the user; password reset revokes all existing sessions.

## API examples

```http
POST /api/v1/auth/forgot-password
Content-Type: application/json

{"email":"rajat@example.com"}
```

```http
POST /api/v1/auth/reset-password
Content-Type: application/json

{"email":"rajat@example.com","code":"123456","newPassword":"NewCorrectHorse456"}
```

## UX and security requirements

- Never display or log the raw reset code.
- Keep the email address entered by the user for the reset submission; use the masked
  destination only for display.
- Disable submit while a request is in flight and prevent duplicate submissions.
- Handle `400 VALIDATION_FAILED`, `401 INVALID_CREDENTIALS`, `422 INVALID_VERIFICATION`,
  and `500 INTERNAL_SERVER_ERROR` using the API error `code`, with a generic recovery
  message where appropriate.
- Offer **Resend code** by repeating the forgot-password request with the same contact
  after the server permits it; replace the countdown using the new `expiresAt`.
- Do not reveal whether an account exists in a user-facing error message.
