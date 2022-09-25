cmake_minimum_required(VERSION 3.5)

add_library(BloomFilter BloomFilter.hpp BloomFilter.cpp)
target_include_directories(BloomFilter PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})