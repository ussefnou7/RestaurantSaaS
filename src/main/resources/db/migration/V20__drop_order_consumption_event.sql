-- order_consumption_event was superseded by the D58 batching design, which reads
-- order_consumption_doc_line directly. The Java entity and repository are already removed.
DROP TABLE IF EXISTS public.order_consumption_event;
