-- Add code column to res_type table and populate it based on label

-- Add the code column
ALTER TABLE res_type ADD COLUMN code VARCHAR(255);

-- Update existing records with computed code (label in lowercase with spaces replaced by underscores)
UPDATE res_type SET code = LOWER(REPLACE(label, ' ', '_'));

-- Make the column NOT NULL
ALTER TABLE res_type ALTER COLUMN code SET NOT NULL;

-- Add unique constraint to code column
ALTER TABLE res_type ADD CONSTRAINT res_type_code_unique UNIQUE (code);

