#ifndef GLOBAL_H
#define GLOBAL_H

#include <netinet/in6.h>

///////////////////////////////////////////////////////////////////////////////
// Global variables
///////////////////////////////////////////////////////////////////////////////

// defined in pcap.c
extern FILE *pcap_file;
extern size_t pcap_record_size;
extern long pcap_file_size;

// defined in ip.c
extern int uid_cache_size;
extern struct uid_cache_entry *uid_cache;
extern int max_tun_msg;

// defined in socks.c
extern char socks5_addr[];
extern int socks5_port;
extern char socks5_username[];
extern char socks5_password[];

// defined in netguard.c
extern int loglevel;

#endif // GLOBAL_H
