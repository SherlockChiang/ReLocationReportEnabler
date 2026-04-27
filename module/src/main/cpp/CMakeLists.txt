cmake_minimum_required(VERSION 3.10.2)
project(relocation_report_enabler)

add_library(${MODULE_NAME} SHARED module.cpp)

target_link_libraries(${MODULE_NAME} android log)