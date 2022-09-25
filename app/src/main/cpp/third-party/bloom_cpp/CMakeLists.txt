cmake_minimum_required(VERSION 3.5)
project(ddg_bloom_cpp)

set(CMAKE_CXX_STANDARD 14)
set(CMAKE_CXX_COMPILER "g++")

enable_testing()

add_subdirectory(src)
add_subdirectory(utils)
add_subdirectory(test)