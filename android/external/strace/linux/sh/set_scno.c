static int
arch_set_scno(struct tcb *tcp, kernel_ulong_t scno)
{
	return upoke(tcp->pid, 4 * (REG_REG0 + 3), scno);
}
