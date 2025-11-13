-- Fix AIProvider table constraints
-- Remove incorrect unique constraint on 'name' column if it exists
-- Ensure only 'displayName' is unique to allow multiple providers of the same type

ALTER TABLE ai_provider DROP KEY IF EXISTS UK_nmrpdeu19ured81litbflx252;

-- Add correct unique constraint on displayName if it doesn't exist
ALTER TABLE ai_provider ADD CONSTRAINT UK_displayName UNIQUE (displayName);
