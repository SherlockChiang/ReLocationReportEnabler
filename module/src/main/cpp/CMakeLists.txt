cmake_minimum_required(VERSION 3.10.2)
project(relocation_report_enabler)

set(CMAKE_CXX_STANDARD 17)

add_library(module SHARED module.cpp)

target_link_libraries(module android log)