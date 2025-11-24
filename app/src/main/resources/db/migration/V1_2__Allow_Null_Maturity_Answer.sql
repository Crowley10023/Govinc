-- Allow maturity_answer_id to be NULL in assessment_control_answer table
-- This enables users to save comments without first selecting an answer

ALTER TABLE assessment_control_answer 
MODIFY COLUMN maturity_answer_id BIGINT NULL;