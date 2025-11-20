-- Additional migration to fix the unique constraint on leader_id if it wasn't removed by V1_1
-- Run this if you still see the error: Duplicate entry for key 'UK_7vv1bxh5ptib49lxwxwgfst7m'

-- Check if the unique constraint still exists and remove it
SET @drop_stmt = (
  SELECT CONCAT('ALTER TABLE org_unit DROP INDEX ', CONSTRAINT_NAME)
  FROM INFORMATION_SCHEMA.KEY_COLUMN_USAGE
  WHERE TABLE_NAME = 'org_unit' 
  AND COLUMN_NAME = 'leader_id'
  AND CONSTRAINT_NAME != 'PRIMARY'
  LIMIT 1
);

-- Execute the drop if constraint exists
IF @drop_stmt IS NOT NULL THEN
  SET @sql = @drop_stmt;
  PREPARE stmt FROM @sql;
  EXECUTE stmt;
  DEALLOCATE PREPARE stmt;
END IF;
