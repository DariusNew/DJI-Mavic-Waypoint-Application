#undef UNICODE

#define WIN32_LEAN_AND_MEAN
#define _CRT_SECURE_NO_WARNINGS

#include <windows.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include <stdlib.h>
#include <stdio.h>
#include "Waypoint.pb.h"
#include "stdafx.h"

// Need to link with Ws2_32.lib
#pragma comment (lib, "Ws2_32.lib")
// #pragma comment (lib, "Mswsock.lib")

#define DEFAULT_BUFLEN 512
#define DEFAULT_PORT "443"

using std::string;
using std::cout;
using std::cin;
using std::getline;
using std::endl;

int __cdecl main(void)
{
	WSADATA wsaData;
	int iResult;

	SOCKET ListenSocket = INVALID_SOCKET;
	SOCKET ClientSocket = INVALID_SOCKET;

	struct addrinfo *result = NULL;
	struct addrinfo hints;

	int iSendResult;
	int sendResult;
	char recvbuf[DEFAULT_BUFLEN];
	int recvbuflen = DEFAULT_BUFLEN;
	bool isRun = true;
	bool isStart = false;

	// Initialize Winsock
	iResult = WSAStartup(MAKEWORD(2, 2), &wsaData);
	if (iResult != 0) {
		printf("WSAStartup failed with error: %d\n", iResult);
		return 1;
	}

	memset(&hints, 0, sizeof(hints));
	hints.ai_family = AF_INET;
	hints.ai_socktype = SOCK_STREAM;
	hints.ai_protocol = IPPROTO_TCP;
	hints.ai_flags = AI_PASSIVE;

	// Resolve the server address and port
	iResult = getaddrinfo(NULL, DEFAULT_PORT, &hints, &result);
	if (iResult != 0) {
		printf("getaddrinfo failed with error: %d\n", iResult);
		WSACleanup();
		system("pause");
		return 1;
	}

	// Create a SOCKET for connecting to server
	ListenSocket = socket(result->ai_family, result->ai_socktype, result->ai_protocol);
	if (ListenSocket == INVALID_SOCKET) {
		printf("socket failed with error: %ld\n", WSAGetLastError());
		freeaddrinfo(result);
		WSACleanup();
		return 1;
	}

	// Setup the TCP listening socket
	iResult = bind(ListenSocket, result->ai_addr, (int)result->ai_addrlen);
	if (iResult == SOCKET_ERROR) {
		printf("bind failed with error: %d\n", WSAGetLastError());
		freeaddrinfo(result);
		closesocket(ListenSocket);
		WSACleanup();
		return 1;
	}

	freeaddrinfo(result);

	iResult = listen(ListenSocket, SOMAXCONN);
	if (iResult == SOCKET_ERROR) {
		printf("listen failed with error: %d\n", WSAGetLastError());
		closesocket(ListenSocket);
		WSACleanup();
		return 1;
	}

	// Accept a client socket
	ClientSocket = accept(ListenSocket, NULL, NULL);
	if (ClientSocket == INVALID_SOCKET) {
		printf("accept failed with error: %d\n", WSAGetLastError());
		closesocket(ListenSocket);
		WSACleanup();
		return 1;
	}

	// No longer need server socket
	closesocket(ListenSocket);

	// Receive until the peer shuts down the connection
	do {

		iResult = recv(ClientSocket, recvbuf, recvbuflen, 0);
		if (iResult > 0) {
			recvbuf[iResult] = '\0';
			string msg = recvbuf;
			cout << msg << endl;

			while (true) {
				cout << "Enter lattitude: ";
				string latstring;
				getline(cin, latstring);
				double lat;
				lat = stold(latstring);

				cout << "Enter longtitude: ";
				string lngstring;
				getline(cin, lngstring);
				double lng;
				lng = stold(lngstring);

				cout << "Enter altitude: ";
				string altstring;
				getline(cin, altstring);
				float alt;
				alt = stof(altstring);

				cout << "Enter speed: ";
				string spdstring;
				getline(cin, spdstring);
				float speed;
				speed = stof(spdstring);

				cout << "Enter loiter duration: ";
				string durationstring;
				getline(cin, durationstring);
				int duration;
				duration = stoi(durationstring);

				cout << "Send to drone? (Y/N)" << endl << "Lat = " << lat << " Long = " << lng << " Alt = " << alt << " Spd = " << speed << " Loiter = " << duration << endl;
				string add;
				getline(cin, add);
				if (add == "Y") {
					
					//char pkt[100];
					//string msg = "WP " + latstring + " " + lngstring + " " + altstring + " " + spdstring + " " + durationstring + " ";
					//sprintf_s(pkt, "%s", msg.c_str());
					//int size = sizeof(pkt);

					mcs::Waypoint waypoint;
					waypoint.set_latitude(lat);
					waypoint.set_longtitude(lng);
					waypoint.set_altitude(alt);
					waypoint.set_speed(speed);
					waypoint.set_loiter(duration);
					size_t size = waypoint.ByteSizeLong() + 4;
					char *pkt = new char[size];
					waypoint.SerializeToArray(pkt, size);
					mcs::Waypoint wp;
					wp.ParseFromArray(pkt, size);
					cout << wp.latitude() << endl << wp.longtitude() << endl << wp.altitude() << endl << wp.speed() << endl << wp.loiter() << endl;
					iSendResult = send(ClientSocket, pkt, size, 0);
					printf("Bytes sent: %d\n", iSendResult);
					
					if (iSendResult == SOCKET_ERROR) {
						printf("send failed with error: %d\n", WSAGetLastError());
						closesocket(ClientSocket);
						WSACleanup();
						return 1;
					}
					break;
				}
			}
			if (!isStart) {

				cout << "Start Mission? Y/N" << endl;
				string confirm;
				getline(cin, confirm);
				if (confirm == "Y") {
					char bts[100];
					string cfm = "Ready ";
					sprintf_s(bts, "%s", cfm.c_str());
					iSendResult = send(ClientSocket, bts, sizeof(bts), 0);

					Sleep(5000);

					string start = "Start ";
					char ats[100];
					sprintf_s(ats, "%s", start.c_str());
					iSendResult = send(ClientSocket, ats, sizeof(ats), 0);
					isStart = true;
				}
				else if (confirm == "N") {
					char bts[100];
					string ntcfm = "Wait ";
					sprintf_s(bts, "%s", ntcfm.c_str());
					iSendResult = send(ClientSocket, bts, sizeof(bts), 0);
				}
			}
			else if (isStart) {

				cout << "Update Mission? Y/N" << endl;
				string confirm;
				getline(cin, confirm);
				if (confirm == "Y") {
					char bts[100];
					string cfm = "Stop ";
					sprintf_s(bts, "%s", cfm.c_str());
					iSendResult = send(ClientSocket, bts, sizeof(bts), 0);

					Sleep(5000);

					char ats[100];
					string update = "Update ";
					sprintf_s(ats, "%s", update.c_str());
					iSendResult = send(ClientSocket, ats, sizeof(ats), 0);

					Sleep(5000);

					char dts[100];
					string ready = "Ready ";
					sprintf_s(dts, "%s", ready.c_str());
					iSendResult = send(ClientSocket, dts, sizeof(dts), 0);

					Sleep(5000);

					char cts[100];
					string start = "Start ";
					sprintf_s(cts, "%s", start.c_str());
					iSendResult = send(ClientSocket, cts, sizeof(cts), 0);
				}
				else if (confirm == "N") {
					char bts[100];
					string ntcfm = "Wait ";
					sprintf_s(bts, "%s", ntcfm.c_str());
					iSendResult = send(ClientSocket, bts, sizeof(bts), 0);
				}

			}
		}
		else if (iResult == 0)
			printf("Connection closing...\n");
		else {
			printf("recv failed with error: %d\n", WSAGetLastError());
			closesocket(ClientSocket);
			WSACleanup();
			return 1;
		}

	} while (isRun);

	// shutdown the connection since we're done
	iResult = shutdown(ClientSocket, SD_SEND);
	if (iResult == SOCKET_ERROR) {
		printf("shutdown failed with error: %d\n", WSAGetLastError());
		closesocket(ClientSocket);
		WSACleanup();
		return 1;
	}

	// cleanup
	closesocket(ClientSocket);
	WSACleanup();

	system("pause");
	return 0;
}