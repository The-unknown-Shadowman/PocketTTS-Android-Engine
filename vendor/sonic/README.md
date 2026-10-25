Sonic speech time-stretch and pitch processing.

Source: https://github.com/waywardgeek/sonic
Pinned upstream commit: `b93885dcb70aae50c6f76b0fe4e0868f029a077e`
License: Apache-2.0 (see LICENSE). Local change: `sonicRealloc` validates non-positive/overflowing sizes and multiplies in `size_t` before allocation. The modification is marked in sonic.c and covered by a host regression with undefined-behavior sanitization.
