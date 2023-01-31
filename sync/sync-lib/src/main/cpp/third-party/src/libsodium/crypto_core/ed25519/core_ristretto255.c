
#include <assert.h>
#include <stdint.h>
#include <string.h>

#include "core_h2c.h"
#include "crypto_core_ed25519.h"
#include "crypto_core_ristretto255.h"
#include "crypto_hash_sha256.h"
#include "private/common.h"
#include "private/ed25519_ref10.h"
#include "randombytes.h"
#include "utils.h"

int
crypto_core_ristretto255_is_valid_point(const unsigned char *p)
{
    ge25519_p3 p_p3;

    if (ristretto255_frombytes(&p_p3, p) != 0) {
        return 0;
    }
    return 1;
}

int
crypto_core_ristretto255_add(unsigned char *r,
                             const unsigned char *p, const unsigned char *q)
{
    ge25519_p3     p_p3, q_p3, r_p3;

    if (ristretto255_frombytes(&p_p3, p) != 0 ||
        ristretto255_frombytes(&q_p3, q) != 0) {
        return -1;
    }
    ge25519_p3_add(&r_p3, &p_p3, &q_p3);
    ristretto255_p3_tobytes(r, &r_p3);

    return 0;
}

int
crypto_core_ristretto255_sub(unsigned char *r,
                             const unsigned char *p, const unsigned char *q)
{
    ge25519_p3     p_p3, q_p3, r_p3;

    if (ristretto255_frombytes(&p_p3, p) != 0 ||
        ristretto255_frombytes(&q_p3, q) != 0) {
        return -1;
    }
    ge25519_p3_sub(&r_p3, &p_p3, &q_p3);
    ristretto255_p3_tobytes(r, &r_p3);

    return 0;
}

int
crypto_core_ristretto255_from_hash(unsigned char *p, const unsigned char *r)
{
    ristretto255_from_hash(p, r);

    return 0;
}

static int
_string_to_element(unsigned char *p,
                   const char *ctx, const unsigned char *msg, size_t msg_len,
                   int hash_alg)
{
    unsigned char h[crypto_core_ristretto255_HASHBYTES];

    if (core_h2c_string_to_hash(h, sizeof h, ctx, msg, msg_len,
                                hash_alg) != 0) {
        return -1;
    }
    ristretto255_from_hash(p, h);

    return 0;
}

int
crypto_core_ristretto255_from_string(unsigned char p[crypto_core_ristretto255_BYTES],
                                     const char *ctx, const unsigned char *msg,
                                     size_t msg_len, int hash_alg)
{
    return _string_to_element(p, ctx, msg, msg_len, hash_alg);
}

int
crypto_core_ristretto255_from_string_ro(unsigned char p[crypto_core_ristretto255_BYTES],
                                        const char *ctx, const unsigned char *msg,
                                        size_t msg_len, int hash_alg)
{
    return crypto_core_ristretto255_from_string(p, ctx, msg, msg_len, hash_alg);
}

void
crypto_core_ristretto255_random(unsigned char *p)
{
    unsigned char h[crypto_core_ristretto255_HASHBYTES];

    randombytes_buf(h, sizeof h);
    (void) crypto_core_ristretto255_from_hash(p, h);
}

void
crypto_core_ristretto255_scalar_random(unsigned char *r)
{
    crypto_core_ed25519_scalar_random(r);
}

int
crypto_core_ristretto255_scalar_invert(unsigned char *recip,
                                       const unsigned char *s)
{
    return crypto_core_ed25519_scalar_invert(recip, s);
}

void
crypto_core_ristretto255_scalar_negate(unsigned char *neg,
                                       const unsigned char *s)
{
    crypto_core_ed25519_scalar_negate(neg, s);
}

void
crypto_core_ristretto255_scalar_complement(unsigned char *comp,
                                           const unsigned char *s)
{
    crypto_core_ed25519_scalar_complement(comp, s);
}

void
crypto_core_ristretto255_scalar_add(unsigned char *z, const unsigned char *x,
                                    const unsigned char *y)
{
    crypto_core_ed25519_scalar_add(z, x, y);
}

void
crypto_core_ristretto255_scalar_sub(unsigned char *z, const unsigned char *x,
                                    const unsigned char *y)
{
    crypto_core_ed25519_scalar_sub(z, x, y);
}

void
crypto_core_ristretto255_scalar_mul(unsigned char *z, const unsigned char *x,
                                    const unsigned char *y)
{
    sc25519_mul(z, x, y);
}

void
crypto_core_ristretto255_scalar_reduce(unsigned char *r,
                                       const unsigned char *s)
{
    crypto_core_ed25519_scalar_reduce(r, s);
}

int
crypto_core_ristretto255_scalar_is_canonical(const unsigned char *s)
{
    return sc25519_is_canonical(s);
}

#define HASH_SC_L 48U

int
crypto_core_ristretto255_scalar_from_string(unsigned char *s,
                                            const char *ctx, const unsigned char *msg,
                                            size_t msg_len, int hash_alg)
{
    unsigned char h[crypto_core_ristretto255_NONREDUCEDSCALARBYTES];
    unsigned char h_be[HASH_SC_L];
    size_t        i;

    if (core_h2c_string_to_hash(h_be, sizeof h_be, ctx, msg, msg_len,
                                hash_alg) != 0) {
        return -1;
    }
    COMPILER_ASSERT(sizeof h >= sizeof h_be);
    for (i = 0U; i < HASH_SC_L; i++) {
        h[i] = h_be[HASH_SC_L - 1U - i];
    }
    memset(&h[i], 0, (sizeof h) - i);
    crypto_core_ristretto255_scalar_reduce(s, h);

    return 0;
}

size_t
crypto_core_ristretto255_bytes(void)
{
    return crypto_core_ristretto255_BYTES;
}

size_t
crypto_core_ristretto255_nonreducedscalarbytes(void)
{
    return crypto_core_ristretto255_NONREDUCEDSCALARBYTES;
}

size_t
crypto_core_ristretto255_hashbytes(void)
{
    return crypto_core_ristretto255_HASHBYTES;
}

size_t
crypto_core_ristretto255_scalarbytes(void)
{
    return crypto_core_ristretto255_SCALARBYTES;
}
