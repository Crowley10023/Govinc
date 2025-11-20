-- Migration 1.1: Allow a single user to lead multiple organization units
-- Removes the UNIQUE constraint on leader_id column to enable one-to-many relationship
-- between users and organization units they lead

-- First, drop any foreign key constraints that reference the unique constraint
ALTER TABLE org_unit DROP FOREIGN KEY IF EXISTS FK_leader_id;

-- Now drop the unique constraint on leader_id if it exists
ALTER TABLE org_unit DROP INDEX IF EXISTS UK_7vv1bxh5ptib49lxwxwgfst7m;

-- The leader_id column can now have multiple rows with the same value
-- This allows a single user to lead multiple organization units
