-- Password reset tokens table for secure token-based password reset flow
CREATE TABLE idp_password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES idp_users(id) ON DELETE CASCADE,
    token_hash VARCHAR(255) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_reset_token_hash ON idp_password_reset_tokens(token_hash);
CREATE INDEX idx_reset_token_user_id ON idp_password_reset_tokens(user_id);
CREATE INDEX idx_reset_token_expires_at ON idp_password_reset_tokens(expires_at);
