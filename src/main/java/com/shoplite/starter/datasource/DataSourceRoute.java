package com.shoplite.starter.datasource;

/**
 * Target database route for read/write splitting.
 */
public enum DataSourceRoute {

	WRITER,
	READER

}
