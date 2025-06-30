SELECT DISTINCT l_shipdate
FROM public.lineitem
WHERE l_shipdate IS NOT NULL
ORDER BY l_shipdate;