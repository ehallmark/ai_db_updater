-- CONNECT TO PROPER DATABASE!
\connect patentdb

-- CREATE THE TABLE FOR PATENT SENTENCE TOKENS
CREATE TABLE paragraph_tokens (
    pub_doc_number varchar(25) not null,
    classifications text[] not null default('{}'::text[]),
    assignees text[] not null default('{}'::text[]),
    tokens text[] not null,
    randomizer double precision not null default(random())
);

-- TO RANDOMIZE ORDER OF DATA PHYSICALLY ON THE DISK
CREATE INDEX paragraph_tokens_random_idx on paragraph_tokens (randomizer);
CREATE INDEX paragraph_tokens_pub_doc_number_idx on paragraph_tokens (pub_doc_number);

CLUSTER paragraph_tokens USING paragraph_tokens_random_idx;