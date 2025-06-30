SELECT DISTINCT
    p.p_size AS part_size,
    p.p_type AS part_type,
    r.r_name AS region
FROM
    public.part p
        JOIN
    public.partsupp ps ON p.p_partkey = ps.ps_partkey
        JOIN
    public.supplier s ON ps.ps_suppkey = s.s_suppkey
        JOIN
    public.nation n ON s.s_nationkey = n.n_nationkey
        JOIN
    public.region r ON n.n_regionkey = r.r_regionkey
WHERE
    ps.ps_supplycost = (
        SELECT MIN(ps2.ps_supplycost)
        FROM public.partsupp ps2
                 JOIN public.supplier s2 ON ps2.ps_suppkey = s2.s_suppkey
                 JOIN public.nation n2 ON s2.s_nationkey = n2.n_nationkey
                 JOIN public.region r2 ON n2.n_regionkey = r2.r_regionkey
        WHERE ps2.ps_partkey = p.p_partkey
          AND r2.r_name = r.r_name
    )
ORDER BY
    p.p_size,
    p.p_type,
    r.r_name;


