-- Migration 1.5: Convert assessments.status from ENUM to VARCHAR(20)
-- The REVIEW status value was added to AssessmentStatus enum, but MySQL/MariaDB
-- ENUM columns do not automatically accept new values via Hibernate ddl-auto=update.
-- This migration converts the column to VARCHAR(20) so any future enum values
-- are accepted without requiring another schema change.

ALTER TABLE assessments
    MODIFY COLUMN status VARCHAR(20) NOT NULL DEFAULT 'OPEN';
